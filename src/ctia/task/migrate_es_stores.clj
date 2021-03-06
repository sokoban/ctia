(ns ctia.task.migrate-es-stores
  (:require [clj-momo.lib.es
             [conn :as conn]
             [document :as es-doc]
             [index :as es-index]]
            [ctia.stores.es.store :refer [store->map]]
            [ctia.stores.es.crud :refer [coerce-to-fn]]
            [clojure.string :as string]
            [clojure.tools.logging :as log]
            [ctia
             [init :refer [init-store-service! log-properties]]
             [properties :as p :refer [properties]]
             [store :refer [stores]]]
            [ctia.entity.entities :refer [entities]]
            [ctia.entity.sighting.schemas :refer [StoredSighting]]
            [ctia.stores.es.crud :refer [coerce-to-fn]]
            [ctia.task.migrations :refer [available-migrations]]
            [schema-tools.core :as st]
            [schema.core :as s]))

(def default-batch-size 100)
(def timeout (* 5 60000))

(defn optimizations-enabled? []
  (get-in @properties
          [:ctia
           :migration
           :optimizations]))

(def all-types
  (assoc (apply merge {}
                (map (fn [[_ {:keys [entity stored-schema]}]]
                       {entity stored-schema}) entities))
         :sighting (st/merge StoredSighting
                             {(s/optional-key :observables_hash) s/Any})))

(defn type->schema [type]
  (if-let [schema (get all-types type)]
    schema
    (do (log/warnf "missing schema definition for: %s" type)
        s/Any)))

(defn compose-migrations
  "compose migrations from a list of keywords"
  [migration-keys]
  (let [migrations
        (vals (select-keys available-migrations
                           migration-keys))]
    (if (seq migrations)
      (apply comp migrations)
      (do (log/warn "target migration not found, copying data")
          (map identity)))))

(defn prefixed-index [index prefix]
  (let [version-trimmed (string/replace index #"^v[^_]*_" "")]
    (format "v%s_%s" prefix version-trimmed)))

(defn source-store-map->target-store-map
  "transform a source store map into a target map,
  essentially updating indexname"
  [store prefix]
  (update store :indexname #(prefixed-index % prefix)))

(defn stores->maps
  "transform store records to maps"
  [stores]
  (into {}
        (map (fn [[store-key store-record]]
               {store-key
                (store->map store-record
                            {:cm (conn/make-connection-manager
                                  {:timeout timeout})})})
             stores)))

(defn source-store-maps->target-store-maps
  "transform target store records to maps"
  [current-stores prefix]
  (into {}
        (map (fn [[sk sr]]
               {sk (source-store-map->target-store-map sr prefix)})
             current-stores)))

(defn setup
  "init properties, start CTIA and its store service"
  []
  (log/info "starting CTIA Stores...")
  (p/init!)
  (log-properties)
  (init-store-service!))

(defn fetch-batch
  "fetch a batch of documents from an es index"
  [{:keys [conn
           indexname
           mapping]}
   batch-size
   offset
   sort-keys]
  (let [params
        (merge
         {:offset (or offset 0)
          :limit batch-size}
         (when sort-keys
           {:search_after sort-keys}))]
    (es-doc/search-docs
     conn
     indexname
     mapping
     nil
     {}
     params)))

(def bulk-max-size (* 5 1024 1024)) ;; 5Mo

(defn store-batch
  "store a batch of documents using a bulk operation"
  [{:keys [conn indexname mapping type]} batch]
  (log/debugf "%s - storing %s records"
              type
              (count batch))
  (let [prepared-docs
        (map #(assoc %
                     :_id (:id %)
                     :_index indexname
                     :_type mapping)
             batch)]

    (es-doc/bulk-create-doc
     conn
     prepared-docs
     "false"
     bulk-max-size)))

(def optimized-settings
  {:index
   {:number_of_replicas 0
    :refresh_interval -1}})

(defn make-target-index-settings [settings]
  (if (optimizations-enabled?)
    (merge
     {:index settings}
     optimized-settings)
    settings))

(defn create-target-store
  "create the target store, pushing its template"
  [target-store]
  (let [wildcard (:indexname target-store)
        settings (get-in target-store [:config :settings])
        index-settings (make-target-index-settings
                        settings)]
    (log/infof "%s - purging indexes: %s"
               (:type target-store)
               wildcard)
    (es-index/delete!
     (:conn target-store)
     wildcard)
    (log/infof "%s - creating index template: %s"
               (:type target-store)
               (:indexname target-store))
    (log/infof "%s - creating index: %s"
               (:type target-store)
               (:indexname target-store))

    (es-index/create-template!
     (:conn target-store)
     (:indexname target-store)
     (:config target-store))

    (es-index/create!
     (:conn target-store)
     (:indexname target-store)
     index-settings)))

(defn revert-optimizations-settings
  [settings]
  {:index (dissoc settings
                  :number_of_shards
                  :analysis)})

(defn migrate-store
  "migrate a single store"
  [current-store
   target-store
   migrations
   batch-size
   confirm?]
  (when confirm?
    (create-target-store target-store))
  (let [store-size (-> (fetch-batch current-store 1 0 nil)
                       :paging
                       :total-hits)
        store-schema (type->schema (keyword (:type target-store)))
        coerce! (coerce-to-fn [store-schema])]
    (log/infof "%s - store size: %s records"
               (:type current-store)
               store-size)

    (loop [offset 0
           sort-keys nil
           migrated-count 0]
      (let [{:keys [data paging]
             :as batch}
            (fetch-batch current-store
                         batch-size
                         offset
                         sort-keys)
            next (:next paging)
            offset (:offset next 0)
            search_after (:sort paging)
            migrated (transduce migrations conj data)
            migrated-count (+ migrated-count
                              (count migrated))]

        (when (seq migrated)
          (try (coerce! migrated)
               (catch Exception e
                 (if-let [errors (some->> (ex-data e) :error (remove nil?))]
                   (let [message
                         (format (str "%s - Invalid batch, certainly a coercion issue "
                                      "errors: %s")
                                 (pr-str (:type current-store))
                                 (pr-str errors))]
                     (log/error message)
                     message)
                   (throw e))))

          (when confirm?
            (store-batch target-store migrated))

          (log/infof "%s - migrated: %s documents"
                     (:type current-store)
                     migrated-count))
        (if next
          (recur offset
                 search_after
                 migrated-count)
          (do (log/infof "%s - finished migrating %s documents"
                         (:type current-store)
                         migrated-count)

              (when (optimizations-enabled?)
                (log/infof "%s - update index settings" (:type current-store))
                (es-index/update-settings! (:conn target-store)
                                           (:indexname target-store)
                                           (revert-optimizations-settings
                                            (get-in target-store [:config :settings])))

                (log/infof "%s - trigger refresh" (:type current-store))
                (es-index/refresh! (:conn target-store)
                                   (:indexname target-store)))))))))

(defn migrate-store-indexes
  "migrate all es store indexes"
  [prefix migrations batch-size confirm?]
  (let [current-stores (stores->maps @stores)
        target-stores
        (source-store-maps->target-store-maps current-stores
                                              prefix)
        migrations (compose-migrations migrations)
        batch-size (or batch-size default-batch-size)]

    (log/infof "migrating stores: %s" (keys current-stores))
    (log/infof "set batch size: %s" batch-size)

    (doseq [[sk sr] current-stores]
      (log/infof "migrating store: %s" sk)
      (migrate-store sr
                     (sk target-stores)
                     migrations
                     batch-size
                     confirm?))))

(defn check-store
  "check a single store"
  [target-store
   batch-size]
  (let [store-schema (type->schema (keyword (:type target-store)))
        coerce! (coerce-to-fn [store-schema])
        store-size (-> (fetch-batch target-store 1 0 nil)
                       :paging
                       :total-hits)]
    (log/infof "%s - store size: %s records"
               (:type target-store)
               store-size)

    (loop [offset 0
           sort-keys nil
           checked-count 0]
      (let [{:keys [data paging]
             :as batch}
            (fetch-batch target-store
                         batch-size
                         offset
                         sort-keys)
            next (:next paging)
            offset (:offset next 0)
            search_after (:sort paging)
            checked (coerce! data)
            checked-count (+ checked-count
                             (count checked))]
        (if next
          (recur offset search_after checked-count)
          (log/infof "%s - finished checking %s documents"
                     (:type target-store)
                     checked-count))))))

(defn check-store-index
  [[sk sr :as store] batch-size]
  (try
    (log/infof "checking store: %s" sk)
    (check-store sr batch-size)
    (catch Exception e
      (if-let [errors (some->> (ex-data e) :error (remove nil?))]
        (let [message
              (format (str "The store %s is invalid, certainly a coercion issue "
                           "errors: %s")
                      sk
                      (pr-str errors))]
          (log/error message)
          message)
        (throw e)))))

(defn check-store-indexes
  "check all new es store indexes"
  [batch-size prefix]
  (let [current-stores (stores->maps @stores)
        target-stores
        (source-store-maps->target-store-maps current-stores
                                              prefix)
        batch-size (or batch-size default-batch-size)]

    (log/infof "checking stores: %s" (keys current-stores))
    (log/infof "set batch size: %s" batch-size)
    (keep #(check-store-index % batch-size) target-stores)))

(defn exit [error?]
  (if error?
    (System/exit -1)
    (System/exit 0)))

(defn run-migration
  [prefix migrations batch-size confirm?]
  (assert prefix "Please provide an indexname prefix for target store creation")
  (assert migrations "Please provide a csv migration list argument")
  (assert batch-size "Please specify a batch size")
  (log/info "migrating all ES Stores")
  (try
    (setup)
    (log/infof "optimizations enabled: %s"
               (optimizations-enabled?))
    (migrate-store-indexes prefix
                           migrations
                           batch-size
                           confirm?)
    (when confirm?
      (when-let [errors (seq (check-store-indexes batch-size prefix))]
        (log/errorf "Schema errors during migration: %s"
                    (pr-str errors))
        (exit true)))
    (log/info "migration complete")
    (catch Exception e
      (log/error e "Unexpected error during migration")
      (exit true)))
  (exit false))

(defn -main
  "invoke with lein run -m ctia.task.migrate-es-stores <prefix> <migrations> <batch-size> <confirm?>"
  [prefix migrations batch-size confirm?]
  (let [confirm? (or (boolean (read-string confirm?)) false)
        batch-size (read-string batch-size)
        migrations (map keyword (string/split migrations #","))]
    (run-migration prefix migrations batch-size confirm?)))
