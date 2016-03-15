(ns cia.events
  (:require [cia.events.schemas :as es]
            [cia.schemas.common :as c]
            [cia.schemas.verdict :as v]
            [clj-time.core :as time]
            [clojure.core.async :as a]
            [schema.core :as s])
  (:import [clojure.core.async Mult]
           [clojure.core.async.impl.protocols Channel]
           [clojure.core.async.impl.buffers FixedBuffer]
           [java.util Map]))

(def shutdown-max-wait-ms (* 1000 60 60))
(def ^:dynamic *event-buffer-size* 1000)

(defonce central-channel (atom nil))

(s/defschema EventChannel
  "This structure holds a channel, its associated buffer, and a multichan"
  {:chan-buf FixedBuffer
   :chan Channel
   :mult Mult})

(s/defn new-event-channel :- EventChannel []
  (let [b (a/buffer *event-buffer-size*)
        c (a/chan b)
        p (a/mult c)]
    {:chan-buf b
     :chan c
     :mult p}))

(defn init! []
  (reset! central-channel (new-event-channel)))

(s/defn shutdown-channel :- Long
  "Shuts down a provided event channel."
  [max-wait-ms :- Long
   {:keys [:chan-buf :chan :mult]} :- EventChannel]
  (let [ch (a/chan (a/dropping-buffer 1))]
    (a/tap mult ch)
    (a/close! chan)
    (loop [timeout (a/timeout max-wait-ms)]
      (let [[val _] (a/alts!! [ch timeout] :priority true)]
        (if (some? val)
          (recur timeout)
          (count chan-buf))))))

(s/defn shutdown! :- Long
  "Close the event channel, waiting up to max-wait-ms for the buffer
   to flush.  Returns the number of items in the buffer after
   shutdown (zero on a successful flush).
   Closes the central channel by default."
  ([]
   (shutdown! shutdown-max-wait-ms))
  ([max-wait-ms :- Long]
   (shutdown-channel max-wait-ms @central-channel)))

(s/defn send-event
  "Send an event to a channel. Use the central channel by default"
  ([event :- es/ModelEventBase]
   (send-event @central-channel event))
  ([{ch :chan} :- EventChannel
    {:keys [owner timestamp http-params] :as event} :- es/ModelEventBase]
   (assert owner "Events cannot be registered without user info")
   (let [event (if timestamp event (assoc event :timestamp (time/now)))]
     (a/>!! ch event))))

(s/defn send-create-event
  "Builds a creation event and sends it to the provided channel. Use the central channel by default."
  ([owner :- s/Str
    http-params :- c/HttpParams  ; maybe { s/Key s/Any }
    model-type :- s/Str
    new-model :- {s/Any s/Any}]
   (send-create-event @central-channel owner http-params model-type new-model))
  ([echan :- EventChannel
    owner :- s/Str
    http-params :- c/HttpParams
    model-type :- s/Str
    new-model :- {s/Any s/Any}]
   (send-event echan {:type es/CreateEventType
                      :owner owner
                      :timestamp (time/now)
                      :http-params http-params
                      :model-type model-type
                      :id (:id new-model)
                      :model new-model})))

(s/defn send-updated-model
  "Builds an updated model event and sends it to the provided channel. Use the central channel by default."
  ([owner :- s/Str
    http-params :- c/HttpParams
    triples :- [es/UpdateTriple]]
   (send-updated-model @central-channel owner http-params triples))
  ([echan :- EventChannel
    owner :- s/Str
    http-params :- c/HttpParams
    triples :- [es/UpdateTriple]]
   (send-event echan {:type es/UpdateEventType
                      :owner owner
                      :timestamp (time/now)
                      :http-params http-params
                      :fields triples})))

(s/defn send-deleted-model
  "Builds a delete event and sends it to the provided channel. Use the central channel by default."
  ([owner :- s/Str
    http-params :- c/HttpParams
    id :- s/Str]
   (send-deleted-model @central-channel owner http-params id))
  ([echan :- EventChannel
    owner :- s/Str
    http-params :- c/HttpParams
    id :- s/Str]
   (send-event echan {:type es/DeleteEventType
                      :owner owner
                      :timestamp (time/now)
                      :id id})))

(s/defn send-verdict-change
  "Builds a verdict change event and sends it to the provided channel. Use the central channel by default."
  ([owner :- s/Str
    http-params :- c/HttpParams
    id :- s/Str
    verdict :- v/Verdict]
   (send-verdict-change @central-channel owner http-params id verdict))
  ([echan :- EventChannel
    owner :- s/Str
    http-params :- c/HttpParams
    id :- s/Str
    verdict :- v/Verdict]
   (send-event echan {:type es/VerdictChangeEventType
                      :owner owner
                      :timestamp (time/now)
                      :judgement_id id
                      :verdict verdict})))
