(ns cia.handler-test
  (:refer-clojure :exclude [get])
  (:require [cia.handler :as handler]
            [cia.test-helpers :refer [delete get post put] :as helpers]
            [clj-http.client :as http]
            [clojure.test :refer [deftest is testing use-fixtures join-fixtures]]
            [cia.schemas.common :as c]))

(use-fixtures :each (join-fixtures [(helpers/fixture-server handler/app)
                                    helpers/fixture-in-memory-store
                                    helpers/fixture-schema-validation]))

(deftest test-version-routes
  (testing "we can request different content types"
    (let [response (get "cia/version" :accept :json)]
      (is (= "/cia" (get-in response [:parsed-body "base"]))))

    (let [response (get "cia/version" :accept :edn)]
      (is (= "/cia" (get-in response [:parsed-body :base]) ))))

  (testing "GET /cia/version"
    (let [response (get "cia/version")]
      (is (= 200 (:status response)))
      (is (= "0.1" (get-in response [:parsed-body :version]))))))

(deftest test-actor-routes
  (testing "POST /cia/actor"
    (let [response (post "cia/actor"
                         :body {:title "actor"
                                :description "description"
                                :type "Hacker"
                                :source "a source"
                                :confidence "High"
                                :associated_actors ["actor-123" "actor-456"]
                                :associated_campaigns ["campaign-444" "campaign-555"]
                                :observed_TTPs ["ttp-333" "ttp-999"]
                                :valid_time {:start_time "2016-02-11T00:40:48.212-00:00"
                                             :end_time "2016-07-11T00:40:48.212-00:00"}})
          actor (:parsed-body response)]
      (is (= 200 (:status response)))
      (is (= {:description "description",
              :type "Hacker",
              :title "actor",
              :confidence "High",
              :source "a source"
              :associated_actors ["actor-123" "actor-456"]
              :associated_campaigns ["campaign-444" "campaign-555"]
              :observed_TTPs ["ttp-333" "ttp-999"]
              :valid_time {:start_time #inst "2016-02-11T00:40:48.212-00:00"
                           :end_time #inst "2016-07-11T00:40:48.212-00:00"}
              :owner "not implemented"}
             (dissoc actor
                     :id
                     :created
                     :modified)))

      (testing "GET /cia/actor/:id"
        (let [response (get (str "cia/actor/" (:id actor)))
              actor (:parsed-body response)]
          (is (= 200 (:status response)))
          (is (= {:description "description",
                  :type "Hacker",
                  :title "actor",
                  :confidence "High",
                  :source "a source"
                  :associated_actors ["actor-123" "actor-456"]
                  :associated_campaigns ["campaign-444" "campaign-555"]
                  :observed_TTPs ["ttp-333" "ttp-999"]
                  :valid_time {:start_time #inst "2016-02-11T00:40:48.212-00:00"
                               :end_time #inst "2016-07-11T00:40:48.212-00:00"}
                  :owner "not implemented"}
                 (dissoc actor
                         :id
                         :created
                         :modified)))))

      (testing "PUT /cia/actor/:id"
        (let [response (put (str "cia/actor/" (:id actor))
                            :body {:title "modified actor"
                                   :description "updated description"
                                   :type "Hacktivist"
                                   :source "a source"
                                   :confidence "High"
                                   :associated_actors ["actor-789"]
                                   :associated_campaigns ["campaign-444" "campaign-555"]
                                   :observed_TTPs ["ttp-333" "ttp-999"]
                                   :valid_time {:start_time "2016-02-11T00:40:48.212-00:00"
                                                :end_time "2016-07-11T00:40:48.212-00:00"}})
              updated-actor (:parsed-body response)]
          (is (= 200 (:status response)))
          (is (= {:id (:id actor)
                  :created (:created actor)
                  :title "modified actor"
                  :description "updated description"
                  :type "Hacktivist"
                  :source "a source"
                  :confidence "High"
                  :associated_actors ["actor-789"]
                  :associated_campaigns ["campaign-444" "campaign-555"]
                  :observed_TTPs ["ttp-333" "ttp-999"]
                  :valid_time {:start_time #inst "2016-02-11T00:40:48.212-00:00"
                               :end_time #inst "2016-07-11T00:40:48.212-00:00"}
                  :owner "not implemented"}
                 (dissoc updated-actor
                         :modified)))))

      (testing "DELETE /cia/actor/:id"
        (let [response (delete (str "cia/actor/" (:id actor)))]
          (is (= 204 (:status response)))
          (let [response (get (str "cia/actor/" (:id actor)))]
            (is (= 404 (:status response)))))))))

(deftest test-campaign-routes
  (testing "POST /cia/campaign"
    (let [response (post "cia/campaign"
                         :body {:title "campaign"
                                :description "description"
                                :type "anything goes here"
                                :intended_effect ["Theft"]
                                :indicators ["indicator-foo" "indicator-bar"]
                                :attribution [{:confidence "High"
                                               :source "source"
                                               :relationship "relationship"
                                               :actor "actor-123"}]
                                :related_incidents [{:confidence "High"
                                                     :source "source"
                                                     :relationship "relationship"
                                                     :incident "incident-222"}]
                                :related_TTPs [{:confidence "High"
                                                :source "source"
                                                :relationship "relationship"
                                                :ttp "ttp-999"}]
                                :valid_time {:start_time "2016-02-11T00:40:48.212-00:00"
                                             :end_time "2016-07-11T00:40:48.212-00:00"}})
          campaign (:parsed-body response)]
      (is (= 200 (:status response)))
      (is (= {:title "campaign"
              :description "description"
              :type "anything goes here"
              :intended_effect ["Theft"]
              :indicators ["indicator-foo" "indicator-bar"]
              :attribution [{:confidence "High"
                             :source "source"
                             :relationship "relationship"
                             :actor "actor-123"}]
              :related_incidents [{:confidence "High"
                                   :source "source"
                                   :relationship "relationship"
                                   :incident "incident-222"}]
              :related_TTPs [{:confidence "High"
                              :source "source"
                              :relationship "relationship"
                              :ttp "ttp-999"}]
              :valid_time {:start_time #inst "2016-02-11T00:40:48.212-00:00"
                           :end_time #inst "2016-07-11T00:40:48.212-00:00"}
              :owner "not implemented"}
             (dissoc campaign
                     :id
                     :created
                     :modified)))

      (testing "GET /cia/campaign/:id"
        (let [response (get (str "cia/campaign/" (:id campaign)))
              campaign (:parsed-body response)]
          (is (= 200 (:status response)))
          (is (= {:title "campaign"
                  :description "description"
                  :type "anything goes here"
                  :intended_effect ["Theft"]
                  :indicators ["indicator-foo" "indicator-bar"]
                  :attribution [{:confidence "High"
                                 :source "source"
                                 :relationship "relationship"
                                 :actor "actor-123"}]
                  :related_incidents [{:confidence "High"
                                       :source "source"
                                       :relationship "relationship"
                                       :incident "incident-222"}]
                  :related_TTPs [{:confidence "High"
                                  :source "source"
                                  :relationship "relationship"
                                  :ttp "ttp-999"}]
                  :valid_time {:start_time #inst "2016-02-11T00:40:48.212-00:00"
                               :end_time #inst "2016-07-11T00:40:48.212-00:00"}
                  :owner "not implemented"}
                 (dissoc campaign
                         :id
                         :created
                         :modified)))))

      (testing "PUT /cia/campaign/:id"
        (let [response (put (str "cia/campaign/" (:id campaign))
                            :body {:title "modified campaign"
                                   :description "different description"
                                   :type "anything goes here"
                                   :intended_effect ["Brand Damage"]
                                   :indicators ["indicator-foo" "indicator-bar"]
                                   :attribution [{:confidence "High"
                                                  :source "source"
                                                  :relationship "relationship"
                                                  :actor "actor-123"}]
                                   :related_incidents [{:confidence "High"
                                                        :source "source"
                                                        :relationship "relationship"
                                                        :incident "incident-222"}]
                                   :related_TTPs [{:confidence "High"
                                                   :source "source"
                                                   :relationship "relationship"
                                                   :ttp "ttp-999"}]
                                   :valid_time {:start_time "2016-02-11T00:40:48.212-00:00"
                                                :end_time "2016-07-11T00:40:48.212-00:00"}})
              updated-campaign (:parsed-body response)]
          (is (= 200 (:status response)))
          (is (= {:id (:id campaign)
                  :created (:created campaign)
                  :title "modified campaign"
                  :description "different description"
                  :type "anything goes here"
                  :intended_effect ["Brand Damage"]
                  :indicators ["indicator-foo" "indicator-bar"]
                  :attribution [{:confidence "High"
                                 :source "source"
                                 :relationship "relationship"
                                 :actor "actor-123"}]
                  :related_incidents [{:confidence "High"
                                       :source "source"
                                       :relationship "relationship"
                                       :incident "incident-222"}]
                  :related_TTPs [{:confidence "High"
                                  :source "source"
                                  :relationship "relationship"
                                  :ttp "ttp-999"}]
                  :valid_time {:start_time #inst "2016-02-11T00:40:48.212-00:00"
                               :end_time #inst "2016-07-11T00:40:48.212-00:00"}
                  :owner "not implemented"}
                 (dissoc updated-campaign
                         :modified)))))

      (testing "DELETE /cia/campaign/:id"
        (let [response (delete (str "cia/campaign/" (:id campaign)))]
          (is (= 204 (:status response)))
          (let [response (get (str "cia/campaign/" (:id campaign)))]
            (is (= 404 (:status response)))))))))

(deftest test-coa-routes
  (testing "POST /cia/coa"
    (let [response (post "cia/coa"
                         :body {:title "coa"
                                :description "description"
                                :type "Eradication"
                                :objective ["foo" "bar"]
                                :valid_time {:start_time "2016-02-11T00:40:48.212-00:00"}})
          coa (:parsed-body response)]
      (is (= 200 (:status response)))
      (is (= {:title "coa"
              :description "description"
              :type "Eradication"
              :objective ["foo" "bar"]
              :valid_time {:start_time #inst "2016-02-11T00:40:48.212-00:00"
                           :end_time #inst "2525-01-01T00:00:00.000-00:00"}
              :owner "not implemented"}
             (dissoc coa
                     :id
                     :created
                     :modified)))

      (testing "GET /cia/coa/:id"
        (let [response (get (str "cia/coa/" (:id coa)))
              coa (:parsed-body response)]
          (is (= 200 (:status response)))
          (is (= {:title "coa"
                  :description "description"
                  :type "Eradication"
                  :objective ["foo" "bar"]
                  :valid_time {:start_time #inst "2016-02-11T00:40:48.212-00:00"
                               :end_time #inst "2525-01-01T00:00:00.000-00:00"}
                  :owner "not implemented"}
                 (dissoc coa
                         :id
                         :created
                         :modified)))))

      (testing "PUT /cia/coa/:id"
        (let [{updated-coa :parsed-body
               status :status}
              (put (str "cia/coa/" (:id coa))
                   :body {:title "updated coa"
                          :description "updated description"
                          :type "Hardening"
                          :objective ["foo" "bar"]
                          :valid_time {:start_time "2016-02-11T00:40:48.212-00:00"}})]
          (is (= 200 status))
          (is (= {:id (:id coa)
                  :created (:created coa)
                  :title "updated coa"
                  :description "updated description"
                  :type "Hardening"
                  :objective ["foo" "bar"]
                  :valid_time {:start_time #inst "2016-02-11T00:40:48.212-00:00"
                               :end_time #inst "2525-01-01T00:00:00.000-00:00"}
                  :owner "not implemented"}
                 (dissoc updated-coa
                         :modified)))))

      (testing "DELETE /cia/coa/:id"
        (let [response (delete (str "/cia/coa/" (:id coa)))]
          (is (= 204 (:status response)))
          (let [response (get (str "/cia/coa/" (:id coa)))]
            (is (= 404 (:status response)))))))))

(deftest test-exploit-target-routes
  (testing "POST /cia/exploit-target"
    (let [response (post "cia/exploit-target"
                         :body {:title "exploit-target"
                                :description "description"
                                :vulnerability [{:title "vulnerability"
                                                 :description "description"}]
                                :potential_COAs ["coa-777" "coa-333"]
                                :related_exploit_targets [{:confidence "High"
                                                           :source "source"
                                                           :relationship "relationship"
                                                           :exploit_target "exploit-target-123"}]
                                :valid_time {:start_time "2016-02-11T00:40:48.212-00:00"}})
          exploit-target (:parsed-body response)]
      (is (= 200 (:status response)))
      (is (= {:title "exploit-target"
              :description "description"
              :vulnerability [{:title "vulnerability"
                               :description "description"}]
              :potential_COAs ["coa-777" "coa-333"]
              :related_exploit_targets [{:confidence "High"
                                         :source "source"
                                         :relationship "relationship"
                                         :exploit_target "exploit-target-123"}]
              :valid_time {:start_time #inst "2016-02-11T00:40:48.212-00:00"
                           :end_time #inst "2525-01-01T00:00:00.000-00:00"}
              :owner "not implemented"}
             (dissoc exploit-target
                     :id
                     :created
                     :modified)))

      (testing "GET /cia/exploit-target/:id"
        (let [response (get (str "cia/exploit-target/" (:id exploit-target)))
              exploit-target (:parsed-body response)]
          (is (= 200 (:status response)))
          (is (= {:title "exploit-target"
                  :description "description"
                  :vulnerability [{:title "vulnerability"
                                   :description "description"}]
                  :potential_COAs ["coa-777" "coa-333"]
                  :related_exploit_targets [{:confidence "High"
                                             :source "source"
                                             :relationship "relationship"
                                             :exploit_target "exploit-target-123"}]
                  :valid_time {:start_time #inst "2016-02-11T00:40:48.212-00:00"
                               :end_time #inst "2525-01-01T00:00:00.000-00:00"}
                  :owner "not implemented"}
                 (dissoc exploit-target
                         :id
                         :created
                         :modified)))))

      (testing "PUT /cia/exploit-target/:id"
        (let [{updated-exploit-target :parsed-body
               status :status}
              (put (str "cia/exploit-target/" (:id exploit-target))
                   :body {:title "updated exploit-target"
                          :description "updated description"
                          :vulnerability [{:title "vulnerability"
                                           :description "description"}]
                          :potential_COAs ["coa-777" "coa-333"]
                          :related_exploit_targets [{:confidence "Medium"
                                                     :source "source"
                                                     :relationship "another relationship"
                                                     :exploit_target "exploit-target-123"}]
                          :valid_time {:start_time "2016-02-11T00:40:48.212-00:00"}})]
          (is (= 200 status))
          (is (= {:id (:id exploit-target)
                  :title "updated exploit-target"
                  :description "updated description"
                  :vulnerability [{:title "vulnerability"
                                   :description "description"}]
                  :potential_COAs ["coa-777" "coa-333"]
                  :related_exploit_targets [{:confidence "Medium"
                                             :source "source"
                                             :relationship "another relationship"
                                             :exploit_target "exploit-target-123"}]
                  :valid_time {:start_time #inst "2016-02-11T00:40:48.212-00:00"
                               :end_time #inst "2525-01-01T00:00:00.000-00:00"}
                  :owner "not implemented"
                  :created (:created exploit-target)}
                 (dissoc updated-exploit-target
                         :modified)))))

      (testing "DELETE /cia/exploit-target/:id"
        (let [response (delete (str "cia/exploit-target/" (:id exploit-target)))]
          (is (= 204 (:status response)))
          (let [response (get (str "cia/exploit-target/" (:id exploit-target)))]
            (is (= 404 (:status response)))))))))

(deftest test-incident-routes
  (testing "POST /cia/incident"
    (let [response (post "cia/incident"
                         :body {:title "incident"
                                :description "description"
                                :confidence "High"
                                :categories ["Denial of Service"
                                             "Improper Usage"]
                                :valid_time {:start_time "2016-02-11T00:40:48.212-00:00"}
                                :related_indicators [{:confidence "High"
                                                      :source "source"
                                                      :relationship "relationship"
                                                      :indicator "indicator-123"}]
                                :related_incidents ["incident-123" "indicent-789"]})
          incident (:parsed-body response)]
      (is (= 200 (:status response)))
      (is (= {:title "incident"
              :description "description"
              :confidence "High"
              :categories ["Denial of Service"
                           "Improper Usage"]
              :valid_time {:start_time #inst "2016-02-11T00:40:48.212-00:00"
                           :end_time #inst "2525-01-01T00:00:00.000-00:00"}
              :related_indicators [{:confidence "High"
                                    :source "source"
                                    :relationship "relationship"
                                    :indicator "indicator-123"}]
              :related_incidents ["incident-123" "indicent-789"]
              :owner "not implemented"}
             (dissoc incident
                     :id
                     :created
                     :modified)))

      (testing "GET /cia/incident/:id"
        (let [response (get (str "cia/incident/" (:id incident)))
              incident (:parsed-body response)]
          (is (= 200 (:status response)))
          (is (= {:title "incident"
                  :description "description"
                  :confidence "High"
                  :categories ["Denial of Service"
                               "Improper Usage"]
                  :valid_time {:start_time #inst "2016-02-11T00:40:48.212-00:00"
                               :end_time #inst "2525-01-01T00:00:00.000-00:00"}
                  :related_indicators [{:confidence "High"
                                        :source "source"
                                        :relationship "relationship"
                                        :indicator "indicator-123"}]
                  :related_incidents ["incident-123" "indicent-789"]
                  :owner "not implemented"}
                 (dissoc incident
                         :id
                         :created
                         :modified)))))

      (testing "PUT /cia/incident/:id"
        (let [{status :status
               updated-incident :parsed-body}
              (put (str "cia/incident/" (:id incident))
                   :body {:title "updated incident"
                          :description "updated description"
                          :confidence "Low"
                          :categories ["Denial of Service"
                                       "Improper Usage"]
                          :valid_time {:start_time "2016-02-11T00:40:48.212-00:00"}
                          :related_indicators [{:confidence "High"
                                                :source "another source"
                                                :relationship "relationship"
                                                :indicator "indicator-234"}]
                          :related_incidents ["incident-123" "indicent-789"]})]
          (is (= 200 status))
          (is (= {:id (:id incident)
                  :created (:created incident)
                  :title "updated incident"
                  :description "updated description"
                  :confidence "Low"
                  :categories ["Denial of Service"
                               "Improper Usage"]
                  :valid_time {:start_time #inst "2016-02-11T00:40:48.212-00:00"
                               :end_time #inst "2525-01-01T00:00:00.000-00:00"}
                  :related_indicators [{:confidence "High"
                                        :source "another source"
                                        :relationship "relationship"
                                        :indicator "indicator-234"}]
                  :related_incidents ["incident-123" "indicent-789"]
                  :owner "not implemented"}
                 (dissoc updated-incident
                         :modified)))))

      (testing "DELETE /cia/incident/:id"
        (let [response (delete (str "cia/incident/" (:id incident)))]
          (is (= 204 (:status response)))
          (let [response (get (str "cia/incident/" (:id incident)))]
            (is (= 404 (:status response)))))))))

(deftest test-indicator-routes
  (testing "POST /cia/indicator"
    (let [response (post "cia/indicator"
                         :body {:title "indicator"
                                :description "description"
                                :producer "producer"
                                :type ["C2" "IP Watchlist"]
                                :valid_time {:start_time "2016-05-11T00:40:48.212-00:00"
                                             :end_time "2016-07-11T00:40:48.212-00:00"}
                                :related_campaigns [{:confidence "High"
                                                     :source "source"
                                                     :relationship "relationship"
                                                     :campaign "campaign-123"}]
                                :related_COAs [{:confidence "High"
                                                :source "source"
                                                :relationship "relationship"
                                                :COA "coa-123"}]
                                :judgements ["judgement-123" "judgement-234"]})
          indicator (:parsed-body response)]
      (is (= 200 (:status response)))
      (is (= {:title "indicator"
              :description "description"
              :producer "producer"
              :type ["C2" "IP Watchlist"]
              :valid_time {:start_time #inst "2016-05-11T00:40:48.212-00:00"
                           :end_time #inst "2016-07-11T00:40:48.212-00:00"}
              :related_campaigns [{:confidence "High"
                                   :source "source"
                                   :relationship "relationship"
                                   :campaign "campaign-123"}]
              :related_COAs [{:confidence "High"
                              :source "source"
                              :relationship "relationship"
                              :COA "coa-123"}]
              :judgements ["judgement-123" "judgement-234"]
              :owner "not implemented"}
             (dissoc indicator
                     :id
                     :created
                     :modified)))

      (testing "GET /cia/indicator/:id"
        (let [response (get (str "cia/indicator/" (:id indicator)))
              indicator (:parsed-body response)]
          (is (= 200 (:status response)))
          (is (= {:title "indicator"
                  :description "description"
                  :producer "producer"
                  :type ["C2" "IP Watchlist"]
                  :valid_time {:start_time #inst "2016-05-11T00:40:48.212-00:00"
                               :end_time #inst "2016-07-11T00:40:48.212-00:00"}
                  :related_campaigns [{:confidence "High"
                                       :source "source"
                                       :relationship "relationship"
                                       :campaign "campaign-123"}]
                  :related_COAs [{:confidence "High"
                                  :source "source"
                                  :relationship "relationship"
                                  :COA "coa-123"}]
                  :judgements ["judgement-123" "judgement-234"]
                  :owner "not implemented"}
                 (dissoc indicator
                         :id
                         :created
                         :modified)))))

      (testing "PUT /cia/indicator/:id"
        (let [{status :status
               updated-indicator :parsed-body}
              (put (str "cia/indicator/" (:id indicator))
                   :body {:title "updated indicator"
                          :description "updated description"
                          :producer "producer"
                          :type ["IP Watchlist"]
                          :valid_time {:start_time "2016-05-11T00:40:48.212-00:00"
                                       :end_time "2016-07-11T00:40:48.212-00:00"}
                          :related_campaigns [{:confidence "Low"
                                               :source "source"
                                               :relationship "relationship"
                                               :campaign "campaign-123"}]
                          :related_COAs [{:confidence "High"
                                          :source "source"
                                          :relationship "relationship"
                                          :COA "coa-123"}]
                          :judgements ["judgement-123" "judgement-234"]})]
          (is (= 200 status))
          (is (= {:id (:id indicator)
                  :created (:created indicator)
                  :title "updated indicator"
                  :description "updated description"
                  :producer "producer"
                  :type ["IP Watchlist"]
                  :valid_time {:start_time #inst "2016-05-11T00:40:48.212-00:00"
                               :end_time #inst "2016-07-11T00:40:48.212-00:00"}
                  :related_campaigns [{:confidence "Low"
                                       :source "source"
                                       :relationship "relationship"
                                       :campaign "campaign-123"}]
                  :related_COAs [{:confidence "High"
                                  :source "source"
                                  :relationship "relationship"
                                  :COA "coa-123"}]
                  :judgements ["judgement-123" "judgement-234"]
                  :owner "not implemented"}
                 (dissoc updated-indicator
                         :modified)))))

      (testing "DELETE /cia/indicator/:id"
        (let [response (delete (str "cia/indicator/" (:id indicator)))]
          ;; Deleting indicators is not allowed
          (is (= 404 (:status response))))))))

(deftest test-judgement-routes
  (testing "POST /cia/judgement"
    (let [response (post "cia/judgement"
                         :body {:observable {:value "1.2.3.4"
                                             :type "ip"}
                                :disposition 2
                                :source "test"
                                :priority 100
                                :severity 100
                                :confidence "Low"
                                :valid_time {:start_time "2016-02-11T00:40:48.212-00:00"}
                                :indicators [{:confidence "High"
                                              :source "source"
                                              :relationship "relationship"
                                              :indicator "indicator-123"}]})
          judgement (:parsed-body response)]
      (is (= 200 (:status response)))
      (is (= {:observable {:value "1.2.3.4"
                           :type "ip"}
              :disposition 2
              :disposition_name "Malicious"
              :priority 100
              :severity 100
              :confidence "Low"
              :source "test"
              :valid_time {:start_time #inst "2016-02-11T00:40:48.212-00:00"
                           :end_time #inst "2525-01-01T00:00:00.000-00:00"}
              :indicators [{:confidence "High"
                            :source "source"
                            :relationship "relationship"
                            :indicator "indicator-123"}]
              :owner "not implemented"}
             (dissoc judgement
                     :id
                     :created)))

      (testing "GET /cia/judgement/:id"
        (let [response (get (str "cia/judgement/" (:id judgement)))
              judgement (:parsed-body response)]
          (is (= 200 (:status response)))
          (is (= {:observable {:value "1.2.3.4"
                               :type "ip"}
                  :disposition 2
                  :disposition_name "Malicious"
                  :priority 100
                  :severity 100
                  :confidence "Low"
                  :source "test"
                  :valid_time {:start_time #inst "2016-02-11T00:40:48.212-00:00"
                               :end_time #inst "2525-01-01T00:00:00.000-00:00"}
                  :indicators [{:confidence "High"
                                :source "source"
                                :relationship "relationship"
                                :indicator "indicator-123"}]
                  :owner "not implemented"}
                 (dissoc judgement
                         :id
                         :created)))))

      (testing "DELETE /cia/judgement/:id"
        (let [temp-judgement (-> (post "cia/judgement"
                                       :body {:indicators ["indicator-123"]
                                              :observable {:value "9.8.7.6"
                                                           :type "ip"}
                                              :disposition 3
                                              :source "test"
                                              :priority 100
                                              :severity 100
                                              :confidence "Low"
                                              :valid_time {:start_time "2016-02-11T00:40:48.212-00:00"}})
                                 :parsed-body)
              response (delete (str "cia/judgement/" (:id temp-judgement)))]
          (is (= 204 (:status response)))
          (let [response (get (str "cia/judgement/" (:id temp-judgement)))]
            (is (= 404 (:status response))))))

      (testing "POST /cia/judgement/:id/feedback"
        (let [response (post (str "cia/judgement/" (:id judgement) "/feedback")
                             :body {:feedback -1
                                    :reason "false positive"})
              feedback (:parsed-body response)]
          (is (= 200 (:status response)))
          (is (= {:judgement (:id judgement),
                  :feedback -1,
                  :reason "false positive"
                  :owner "not implemented"}
                 (dissoc feedback
                         :id
                         :created))))

        (testing "GET /cia/judgement/:id/feedback"
          ;; create some more feedbacks
          (let [response (post "cia/judgement"
                               :body {:indicators ["indicator-222"]
                                      :observable {:value "4.5.6.7"
                                                   :type "ip"}
                                      :disposition 1
                                      :source "test"})
                another-judgement (:parsed-body response)]
            (post (str "cia/judgement/" (:id another-judgement) "/feedback")
                  :body {:feedback 0
                         :reason "yolo"}))
          (post (str "cia/judgement/" (:id judgement) "/feedback")
                :body {:feedback 1
                       :reason "true positive"})

          (let [response (get (str "cia/judgement/" (:id judgement) "/feedback"))
                feedbacks (:parsed-body response)]
            (is (= 200 (:status response)))
            (is (= [{:judgement (:id judgement),
                     :feedback -1,
                     :reason "false positive"
                     :owner "not implemented"}
                    {:judgement (:id judgement),
                     :feedback 1,
                     :reason "true positive"
                     :owner "not implemented"}]
                   (map #(dissoc % :id :created)
                        feedbacks)))))))))

(deftest test-judgement-routes-for-dispositon-determination
  (testing "POST a judgement with dispositon (id)"
    (let [response (post "cia/judgement"
                         :body {:observable {:value "1.2.3.4"
                                             :type "ip"}
                                :disposition 2
                                :source "test"
                                :priority 100
                                :severity 100
                                :confidence "Low"
                                :valid_time {:start_time "2016-02-11T00:40:48.212-00:00"}})
          judgement (:parsed-body response)]
      (is (= 200 (:status response)))
      (is (= {:observable {:value "1.2.3.4"
                           :type "ip"}
              :disposition 2
              :disposition_name "Malicious"
              :source "test"
              :priority 100
              :severity 100
              :confidence "Low"
              :valid_time {:start_time #inst "2016-02-11T00:40:48.212-00:00"
                           :end_time #inst "2525-01-01T00:00:00.000-00:00"}
              :owner "not implemented"}
             (dissoc judgement
                     :id
                     :created)))))

  (testing "POST a judgement with disposition_name"
    (let [response (post "cia/judgement"
                         :body {:observable {:value "1.2.3.4"
                                             :type "ip"}
                                :disposition_name "Malicious"
                                :source "test"
                                :priority 100
                                :severity 100
                                :confidence "Low"
                                :valid_time {:start_time "2016-02-11T00:40:48.212-00:00"}})
          judgement (:parsed-body response)]
      (is (= 200 (:status response)))
      (is (= {:observable {:value "1.2.3.4"
                           :type "ip"}
              :disposition 2
              :disposition_name "Malicious"
              :source "test"
              :priority 100
              :severity 100
              :confidence "Low"
              :valid_time {:start_time #inst "2016-02-11T00:40:48.212-00:00"
                           :end_time #inst "2525-01-01T00:00:00.000-00:00"}
              :owner "not implemented"}
             (dissoc judgement
                     :id
                     :created)))))

  (testing "POST a judgement without disposition"
    (let [response (post "cia/judgement"
                         :body {:observable {:value "1.2.3.4"
                                             :type "ip"}
                                :source "test"
                                :priority 100
                                :severity 100
                                :confidence "Low"
                                :valid_time {:start_time "2016-02-11T00:40:48.212-00:00"}})
          judgement (:parsed-body response)]
      (is (= 200 (:status response)))
      (is (= {:observable {:value "1.2.3.4"
                           :type "ip"}
              :disposition 5
              :disposition_name "Unknown"
              :source "test"
              :priority 100
              :severity 100
              :confidence "Low"
              :valid_time {:start_time #inst "2016-02-11T00:40:48.212-00:00"
                           :end_time #inst "2525-01-01T00:00:00.000-00:00"}
              :owner "not implemented"}
             (dissoc judgement
                     :id
                     :created)))))

  (testing "POST a judgement with mismatching disposition/disposition_name"
    (let [response (post "cia/judgement"
                         :body {:observable {:value "1.2.3.4"
                                             :type "ip"}
                                :disposition 1
                                :disposition_name "Unknown"
                                :source "test"
                                :priority 100
                                :severity 100
                                :confidence "Low"
                                :valid_time {:start_time "2016-02-11T00:40:48.212-00:00"}})]
      (is (= 400 (:status response)))
      (is (= {:error "Mismatching :dispostion and dispositon_name for judgement",
              :judgement {:observable {:value "1.2.3.4"
                                       :type "ip"}
                          :disposition 1
                          :disposition_name "Unknown"
                          :source "test"
                          :priority 100
                          :severity 100
                          :confidence "Low"
                          :valid_time {:start_time #inst "2016-02-11T00:40:48.212-00:00"}}}
             (:parsed-body response))))))

(deftest test-observable-judgements-route

  (testing "test setup: create a judgement (1)"
    (let [response (post "cia/judgement"
                         :body {:indicators []
                                :observable {:value "1.2.3.4"
                                             :type "ip"}
                                :disposition 2
                                :source "test"
                                :priority 100
                                :severity 100
                                :confidence "Low"
                                :valid_time {:start_time "2016-02-12T00:00:00.000-00:00"}})]
      (is (= 200 (:status response)))))
  (testing "test setup: create a judgement (2)"
    (let [response (post "cia/judgement"
                         :body {:indicators []
                                :observable {:value "10.0.0.1"
                                             :type "ip"}
                                :disposition 2
                                :source "test"
                                :priority 100
                                :severity 100
                                :confidence "Low"
                                :valid_time {:start_time "2016-02-12T00:00:00.000-00:00"}})]
      (is (= 200 (:status response)))))
  (testing "test setup: create a judgement (3)"
    (let [response (post "cia/judgement"
                         :body {:indicators []
                                :observable {:value "10.0.0.1"
                                             :type "ip"}
                                :disposition 1
                                :source "test"
                                :priority 50
                                :severity 60
                                :confidence "High"
                                :valid_time {:start_time "2016-02-11T00:00:00.000-00:00"}})]
      (is (= 200 (:status response)))))

  (testing "GET /cia/:observable_type/:observable_value/judgements"
    (let [response (get "cia/ip/10.0.0.1/judgements")
          judgements (:parsed-body response)]
      (is (= 200 (:status response)))
      (is (= [{:indicators []
               :observable {:value "10.0.0.1"
                            :type "ip"}
               :disposition 2
               :disposition_name "Malicious"
               :source "test"
               :priority 100
               :severity 100
               :confidence "Low"
               :valid_time {:start_time #inst "2016-02-12T00:00:00.000"
                            :end_time #inst "2525-01-01T00:00:00.000-00:00"}
               :owner "not implemented"}
              {:indicators []
               :observable {:value "10.0.0.1"
                            :type "ip"}
               :disposition 1
               :disposition_name "Clean"
               :source "test"
               :priority 50
               :severity 60
               :confidence "High"
               :valid_time {:start_time #inst "2016-02-11T00:00:00.000-00:00"
                            :end_time #inst "2525-01-01T00:00:00.000-00:00"}
               :owner "not implemented"}]
             (->> judgements
                  (map #(dissoc % :id :created))))))))

(deftest test-observable-indicators-and-sightings-routes

  (let [{judgement-1 :parsed-body
         judgement-1-status :status}
        (post "cia/judgement"
              :body {:indicators []
                     :observable {:value "1.2.3.4"
                                  :type "ip"}
                     :disposition 2
                     :source "test"
                     :priority 100
                     :severity 100
                     :confidence "Low"
                     :valid_time {:start_time "2016-02-01T00:00:00.000-00:00"}})

        {indicator-1-status :status}
        (post "cia/indicator"
              :body {:title "indicator"
                     :judgements [(:id judgement-1)]
                     :sightings [{:timestamp "2016-02-01T00:00:00.000-00:00"
                                  :source "foo"
                                  :confidence "Medium"}
                                 {:timestamp "2016-02-01T12:00:00.000-00:00"
                                  :source "bar"
                                  :confidence "High"}]
                     :description "description"
                     :producer "producer"
                     :type ["C2" "IP Watchlist"]
                     :valid_time {:end_time "2016-02-12T00:00:00.000-00:00"}})

        {judgement-2 :parsed-body
         judgement-2-status :status}
        (post "cia/judgement"
              :body {:indicators []
                     :observable {:value "10.0.0.1"
                                  :type "ip"}
                     :disposition 2
                     :source "test"
                     :priority 100
                     :severity 100
                     :confidence "Low"
                     :valid_time {:start_time "2016-02-01T00:00:00.000-00:00"}})

        {indicator-2-status :status}
        (post "cia/indicator"
              :body {:title "indicator"
                     :judgements [(:id judgement-2)]
                     :sightings [{:timestamp "2016-02-04T12:00:00.000-00:00"
                                  :source "spam"
                                  :confidence "None"}]
                     :description "description"
                     :producer "producer"
                     :type ["C2" "IP Watchlist"]
                     :valid_time {:start_time "2016-01-12T00:00:00.000-00:00"
                                  :end_time "2016-02-12T00:00:00.000-00:00"}})

        {judgement-3 :parsed-body
         judgement-3-status :status}
        (post "cia/judgement"
              :body {:indicators []
                     :observable {:value "10.0.0.1"
                                  :type "ip"}
                     :disposition 2
                     :source "test"
                     :priority 100
                     :severity 100
                     :confidence "Low"
                     :valid_time {:start_time "2016-02-01T00:00:00.000-00:00"}})

        {indicator-3-status :status}
        (post "cia/indicator"
              :body {:title "indicator"
                     :judgements [{:judgement (:id judgement-3)
                                   :confidence "High"}]
                     :sightings [{:timestamp "2016-02-05T01:00:00.000-00:00"
                                  :source "foo"
                                  :confidence "High"}
                                 {:timestamp "2016-02-05T02:00:00.000-00:00"
                                  :source "bar"
                                  :confidence "Low"}]
                     :description "description"
                     :producer "producer"
                     :type ["C2" "IP Watchlist"]
                     :valid_time {:start_time "2016-01-11T00:00:00.000-00:00"
                                  :end_time "2016-02-11T00:00:00.000-00:00"}})]

    (testing "Test setup succeeded"
      (is (= 200 judgement-1-status))
      (is (= 200 indicator-1-status))
      (is (= 200 judgement-2-status))
      (is (= 200 indicator-2-status))
      (is (= 200 judgement-3-status))
      (is (= 200 indicator-3-status)))

    (testing "GET /cia/:observable_type/:observable_value/indicators"
      (let [response (get "cia/ip/10.0.0.1/indicators")
            indicators (:parsed-body response)]
        (is (= 200 (:status response)))
        (is (= [{:title "indicator"
                 :judgements [(:id judgement-2)]
                 :sightings [{:timestamp #inst "2016-02-04T12:00:00.000-00:00"
                              :source "spam"
                              :confidence "None"}]
                 :description "description"
                 :producer "producer"
                 :type ["C2" "IP Watchlist"]
                 :valid_time {:start_time #inst "2016-01-12T00:00:00.000-00:00"
                              :end_time #inst "2016-02-12T00:00:00.000-00:00"}
                 :owner "not implemented"}
                {:title "indicator"
                 :judgements [{:judgement (:id judgement-3)
                               :confidence "High"}]
                 :sightings [{:timestamp #inst "2016-02-05T01:00:00.000-00:00"
                              :source "foo"
                              :confidence "High"}
                             {:timestamp #inst "2016-02-05T02:00:00.000-00:00"
                              :source "bar"
                              :confidence "Low"}]
                 :description "description"
                 :producer "producer"
                 :type ["C2" "IP Watchlist"]
                 :valid_time {:start_time #inst "2016-01-11T00:00:00.000-00:00"
                              :end_time #inst "2016-02-11T00:00:00.000-00:00"}
                 :owner "not implemented"}]
               (->> indicators
                    (map #(dissoc % :id :created :modified))))))))

  (testing "GET /cia/:observable_type/:observable_value/sightings"
    (let [response (get "cia/ip/10.0.0.1/sightings")
          sightings (:parsed-body response)]
      (is (= 200 (:status response)))
      (is (= [{:timestamp #inst "2016-02-04T12:00:00.000-00:00"
               :source "spam"
               :confidence "None"}
              {:timestamp #inst "2016-02-05T01:00:00.000-00:00"
               :source "foo"
               :confidence "High"}
              {:timestamp #inst "2016-02-05T02:00:00.000-00:00"
               :source "bar"
               :confidence "Low"}]
             sightings)))))

(deftest test-observable-verdict-route

  (testing "test setup: create a judgement (1)"
    ;; Incorrect observable
    (let [response (post "cia/judgement"
                         :body {:indicators []
                                :observable {:value "127.0.0.1"
                                             :type "ip"}
                                :disposition 1
                                :source "test"
                                :priority 100
                                :severity 100
                                :confidence "Low"
                                :valid_time {:start_time "2016-02-12T00:00:00.000-00:00"}})]
      (is (= 200 (:status response)))))

  (testing "test setup: create a judgement (2)"
    ;; Lower priority
    (let [response (post "cia/judgement"
                         :body {:observable {:value "10.0.0.1"
                                             :type "ip"}
                                :disposition 1
                                :source "test"
                                :priority 90
                                :severity 100
                                :confidence "Low"
                                :valid_time {:start_time "2016-02-12T00:00:00.000-00:00"}})]
      (is (= 200 (:status response)))))

  (testing "test setup: create a judgement (3)"
    ;; Wrong disposition
    (let [response (post "cia/judgement"
                         :body {:observable {:value "10.0.0.1"
                                             :type "ip"}
                                :disposition 3
                                :source "test"
                                :priority 99
                                :severity 100
                                :confidence "Low"
                                :valid_time {:start_time "2016-02-12T00:00:00.000-00:00"}})]
      (is (= 200 (:status response)))))

  (testing "test setup: create a judgement (4)"
    ;; Loses a tie because of its timestamp being later
    (let [response (post "cia/judgement"
                         :body {:observable {:value "10.0.0.1"
                                             :type "ip"}
                                :disposition 2
                                :source "test"
                                :priority 99
                                :severity 100
                                :confidence "Low"
                                :valid_time {:start_time "2016-02-12T00:00:00.000-00:01"}})
          judgement-1 (:parsed-body response)]
      (is (= 200 (:status response)))))

  (testing "with a highest-priority judgement"
    (let [response (post "cia/judgement"
                         :body {:observable {:value "10.0.0.1"
                                             :type "ip"}
                                :disposition 2
                                :source "test"
                                :priority 99
                                :severity 100
                                :confidence "Low"
                                :valid_time {:start_time "2016-02-12T00:00:00.000-00:00"}})
          judgement-1 (:parsed-body response)]
      (is (= 200 (:status response))) ;; success creating judgement

      (testing "GET /cia/:observable_type/:observable_value/verdict"
        (let [response (get "cia/ip/10.0.0.1/verdict")
              verdict (:parsed-body response)]
          (is (= 200 (:status response)))
          (is (= {:disposition 2
                  :disposition_name "Malicious"
                  :judgement (:id judgement-1)}
                 verdict)))))))

(deftest test-observable-verdict-route-2
  ;; This test case catches a bug that was in the in-memory store
  ;; It tests the code path where priority is equal but dispositions differ
  (testing "test setup: create a judgement (1)"
    (let [response (post "cia/judgement"
                         :body {:observable {:value "string",
                                             :type "device"},
                                :reason_uri "string",
                                :source "string",
                                :disposition 1,
                                :reason "string",
                                :source_uri "string",
                                :priority 99,
                                :severity 50,
                                :valid_time {:start_time "2016-02-12T14:56:26.814-00:00"
                                             :end_time "2016-02-12T14:56:26.719-00:00"}
                                :confidence "Medium"})]
      (is (= 200 (:status response)))))
  (testing "with a verdict judgement"
    (let [response (post "cia/judgement"
                         :body {:observable {:value "10.0.0.1",
                                             :type "ip"},
                                :reason_uri "string",
                                :source "string",
                                :disposition 2,
                                :reason "string",
                                :source_uri "string",
                                :priority 99,
                                :severity 50,
                                :valid_time {:start_time "2016-02-12T14:56:26.814-00:00"}
                                :confidence "Medium"})
          judgement (:parsed-body response)]
      (is (= 200 (:status response)))

      (testing "GET /cia/:observable_type/:observable_value/verdict"
        (with-redefs [clj-time.core/now (constantly (c/timestamp "2016-02-12T15:42:58.232-00:00"))]
          (let [response (get "cia/ip/10.0.0.1/verdict")
                verdict (:parsed-body response)]
            (is (= 200 (:status response)))
            (is (= {:disposition 2
                    :disposition_name "Malicious"
                    :judgement (:id judgement)}
                   verdict))))))))

(deftest test-ttp-routes
  (testing "POST /cia/ttp"
    (let [response (post "cia/ttp"
                         :body {:title "ttp"
                                :description "description"
                                :type "foo"
                                :indicators ["indicator-1" "indicator-2"]
                                :exploit_targets ["exploit-target-123"
                                                  "exploit-target-234"]
                                :valid_time {:start_time "2016-02-11T00:40:48.212-00:00"
                                             :end_time "2016-07-11T00:40:48.212-00:00"}})
          ttp (:parsed-body response)]
      (is (= 200 (:status response)))
      (is (= {:title "ttp"
              :description "description"
              :type "foo"
              :indicators ["indicator-1" "indicator-2"]
              :exploit_targets ["exploit-target-123"
                                "exploit-target-234"]
              :valid_time {:start_time #inst "2016-02-11T00:40:48.212-00:00"
                           :end_time #inst "2016-07-11T00:40:48.212-00:00"}
              :owner "not implemented"}
             (dissoc ttp
                     :id
                     :created
                     :modified)))

      (testing "GET /cia/ttp/:id"
        (let [response (get (str "cia/ttp/" (:id ttp)))
              ttp (:parsed-body response)]
          (is (= 200 (:status response)))
          (is (= {:title "ttp"
                  :description "description"
                  :type "foo"
                  :indicators ["indicator-1" "indicator-2"]
                  :exploit_targets ["exploit-target-123"
                                    "exploit-target-234"]
                  :valid_time {:start_time #inst "2016-02-11T00:40:48.212-00:00"
                               :end_time #inst "2016-07-11T00:40:48.212-00:00"}
                  :owner "not implemented"}
                 (dissoc ttp
                         :id
                         :created
                         :modified)))))

      (testing "PUT /cia/ttp/:id"
        (let [{status :status
               updated-ttp :parsed-body}
              (put (str "cia/ttp/" (:id ttp))
                   :body {:title "updated ttp"
                          :description "updated description"
                          :type "bar"
                          :indicators ["indicator-1" "indicator-2"]
                          :exploit_targets ["exploit-target-123"
                                            "exploit-target-234"]
                          :valid_time {:start_time "2016-02-11T00:40:48.212-00:00"
                                       :end_time "2016-07-11T00:40:48.212-00:00"}})]
          (is (= 200 status))
          (is (= {:id (:id ttp)
                  :created (:created ttp)
                  :title "updated ttp"
                  :description "updated description"
                  :type "bar"
                  :indicators ["indicator-1" "indicator-2"]
                  :exploit_targets ["exploit-target-123"
                                    "exploit-target-234"]
                  :valid_time {:start_time #inst "2016-02-11T00:40:48.212-00:00"
                               :end_time #inst "2016-07-11T00:40:48.212-00:00"}
                  :owner "not implemented"}
                 (dissoc updated-ttp
                         :modified)))))

      (testing "DELETE /cia/ttp/:id"
        (let [response (delete (str "cia/ttp/" (:id ttp)))]
          (is (= 204 (:status response)))
          (let [response (get (str "cia/ttp/" (:id ttp)))]
            (is (= 404 (:status response)))))))))
