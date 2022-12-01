(ns com.wsscode.pathom3.demos.repro170
  (:require
   [com.wsscode.pathom3.error :as p.error]
   [com.wsscode.pathom.viz.ws-connector.core :as-alias pvc]
   [com.wsscode.pathom3.connect.indexes :as pci]
   [com.wsscode.pathom3.connect.operation :as pco]
   [com.wsscode.pathom3.interface.eql :as p.eql]
   [com.wsscode.pathom3.format.shape-descriptor :as p.shape]))

(def resolvers-without-resolve
  '[#::pco{:op-name
           db-gravie.member-plan->db-gravie.employer/employer-id->id--alias,
           :input
           [:db-gravie.member-plan/employer-id],
           :output
           [:db-gravie.employer/id]}
    #::pco{:op-name
           db-gravie.member-plan-participant->db-gravie.member-plan/member-plan-id->id--alias,
           :input
           [:db-gravie.member-plan-participant/member-plan-id],
           :output
           [:db-gravie.member-plan/id]}
    #::pco{:op-name
           db-gravie.member-plan-participant->db-gravie.person/participant-id->id--alias,
           :input
           [:db-gravie.member-plan-participant/participant-id],
           :output
           [:db-gravie.person/id]}
    #::pco{:op-name
           db-gravie.member-plan-financial->gravie.member-plan-financial/monthly-employer-contribution--alias,
           :input
           [:db-gravie.member-plan-financial/monthly-employer-contribution],
           :output
           [:gravie.member-plan-financial/monthly-employer-contribution]}
    #::pco{:op-name
           db-gravie.person->gravie.person/ssn--alias,
           :input
           [:db-gravie.person/ssn],
           :output
           [:gravie.person/ssn]}
    #::pco{:op-name
           gravie.person->db-gravie.person/ssn--alias,
           :input [:gravie.person/ssn],
           :output
           [:db-gravie.person/ssn]}
    #::pco{:op-name
           db-gravie.member-plan->gravie.member-plan/effective-thru-date--alias,
           :input
           [:db-gravie.member-plan/effective-thru-date],
           :output
           [:gravie.member-plan/effective-thru-date]}
    #::pco{:op-name
           db-gravie.member-plan-financial->gravie.member-plan-financial/monthly-plan-premium--alias,
           :input
           [:db-gravie.member-plan-financial/monthly-plan-premium],
           :output
           [:gravie.member-plan-financial/monthly-plan-premium]}
    #::pco{:op-name
           db-gravie.person->gravie.person/birth-date--alias,
           :input
           [:db-gravie.person/birth-date],
           :output
           [:gravie.person/birth-date]}
    #::pco{:op-name
           gravie.person->db-gravie.person/birth-date--alias,
           :input
           [:gravie.person/birth-date],
           :output
           [:db-gravie.person/birth-date]}
    #::pco{:op-name
           db-gravie.person->gravie.person/external-id--alias,
           :input
           [:db-gravie.person/external-id],
           :output
           [:gravie.person/external-id]}
    #::pco{:op-name
           gravie.person->db-gravie.person/external-id--alias,
           :input
           [:gravie.person/external-id],
           :output
           [:db-gravie.person/external-id]}
    #::pco{:op-name
           db-gravie.member-plan->gravie.member-plan/effective-from-date--alias,
           :input
           [:db-gravie.member-plan/effective-from-date],
           :output
           [:gravie.member-plan/effective-from-date]}
    #::pco{:op-name
           db-gravie.person->gravie.person/last-name--alias,
           :input
           [:db-gravie.person/last-name],
           :output
           [:gravie.person/last-name]}
    #::pco{:op-name
           gravie.person->db-gravie.person/last-name--alias,
           :input
           [:gravie.person/last-name],
           :output
           [:db-gravie.person/last-name]}
    #::pco{:op-name
           db-gravie.member-plan->gravie.member-plan/external-id--alias,
           :input
           [:db-gravie.member-plan/external-id],
           :output
           [:gravie.member-plan/external-id]}
    #::pco{:op-name
           db-gravie.member-plan->gravie.member-plan/status--alias,
           :input
           [:db-gravie.member-plan/status],
           :output
           [:gravie.member-plan/status]}
    #::pco{:op-name
           db-gravie.person->gravie.person/phone--alias,
           :input
           [:db-gravie.person/phone],
           :output
           [:gravie.person/phone]}
    #::pco{:op-name
           gravie.person->db-gravie.person/phone--alias,
           :input
           [:gravie.person/phone],
           :output
           [:db-gravie.person/phone]}
    #::pco{:op-name
           db-gravie.member-plan->gravie.member-plan/member-id--alias,
           :input
           [:db-gravie.member-plan/member-id],
           :output
           [:gravie.member-plan/member-id]}
    #::pco{:op-name
           db-gravie.member-plan->gravie.member-plan/product-type--alias,
           :input
           [:db-gravie.member-plan/product-type],
           :output
           [:gravie.member-plan/product-type]}
    #::pco{:op-name
           db-gravie.member-plan->gravie.member-plan/network-url--alias,
           :input
           [:db-gravie.member-plan/network-url],
           :output
           [:gravie.member-plan/network-url]}
    #::pco{:op-name
           db-gravie.member-plan-financial->gravie.member-plan-financial/monthly-premium-reduction--alias,
           :input
           [:db-gravie.member-plan-financial/monthly-premium-reduction],
           :output
           [:gravie.member-plan-financial/monthly-premium-reduction]}
    #::pco{:op-name
           db-gravie.member-plan->gravie.member-plan/enrollment-location--alias,
           :input
           [:db-gravie.member-plan/enrollment-location],
           :output
           [:gravie.member-plan/enrollment-location]}
    #::pco{:op-name
           db-gravie.person->gravie.person/first-name--alias,
           :input
           [:db-gravie.person/first-name],
           :output
           [:gravie.person/first-name]}
    #::pco{:op-name
           gravie.person->db-gravie.person/first-name--alias,
           :input
           [:gravie.person/first-name],
           :output
           [:db-gravie.person/first-name]}
    #::pco{:op-name
           db-gravie.member-plan->gravie.member-plan/name--alias,
           :input
           [:db-gravie.member-plan/name],
           :output
           [:gravie.member-plan/name]}
    #::pco{:op-name
           db-gravie.member-plan-financial->gravie.member-plan-financial/monthly-federal-subsidy--alias,
           :input
           [:db-gravie.member-plan-financial/monthly-federal-subsidy],
           :output
           [:gravie.member-plan-financial/monthly-federal-subsidy]}
    #::pco{:op-name
           db-gravie.person->gravie.person/virtual-bank-account-id--alias,
           :input
           [:db-gravie.person/virtual-bank-account-id],
           :output
           [:gravie.person/virtual-bank-account-id]}
    #::pco{:op-name
           gravie.person->db-gravie.person/virtual-bank-account-id--alias,
           :input
           [:gravie.person/virtual-bank-account-id],
           :output
           [:db-gravie.person/virtual-bank-account-id]}
    #::pco{:op-name
           db-gravie.person->gravie.person/id--alias,
           :input
           [:db-gravie.person/id],
           :output [:gravie.person/id]}
    #::pco{:op-name
           gravie.person->db-gravie.person/id--alias,
           :input [:gravie.person/id],
           :output
           [:db-gravie.person/id]}
    #::pco{:op-name
           db-gravie.member-plan-participant->gravie.member-plan-participant/tpa-member-number-p1--alias,
           :input
           [:db-gravie.member-plan-participant/tpa-member-number-p1],
           :output
           [:gravie.member-plan-participant/tpa-member-number-p1]}
    #::pco{:op-name
           gravie.person.resolver/person-external-id->person,
           :input
           [:gravie.person/external-id],
           :output
           [:db-gravie.person/bank-account-id
            :db-gravie.person/birth-date
            :db-gravie.person/class
            :db-gravie.person/email
            :db-gravie.person/external-id
            :db-gravie.person/first-name
            :db-gravie.person/hpa-member-number
            :db-gravie.person/id
            :db-gravie.person/last-name
            :db-gravie.person/phone
            :db-gravie.person/ssn
            :db-gravie.person/timezone
            :db-gravie.person/user-id
            :db-gravie.person/virtual-bank-account-id]}
    #::pco{:op-name
           gravie.person.resolver/person-id->person,
           :input
           [:db-gravie.person/id],
           :output
           [:db-gravie.person/bank-account-id
            :db-gravie.person/birth-date
            :db-gravie.person/class
            :db-gravie.person/email
            :db-gravie.person/external-id
            :db-gravie.person/first-name
            :db-gravie.person/hpa-member-number
            :db-gravie.person/id
            :db-gravie.person/last-name
            :db-gravie.person/phone
            :db-gravie.person/ssn
            :db-gravie.person/timezone
            :db-gravie.person/user-id
            :db-gravie.person/virtual-bank-account-id]}
    #::pco{:op-name
           gravie.person.resolver/person-exists?,
           :input
           [:db-gravie.person/class],
           :output
           [:gravie.person/exists?]}
    #::pco{:op-name
           gravie.person.resolver/person-is-member?,
           :input
           [:db-gravie.person/class],
           :output
           [:gravie.person/member?
            :gravie.person/type]}
    #::pco{:op-name
           gravie.person.resolver/person->id-number,
           :input
           [:db-gravie.person/hpa-member-number
            :gravie.member-plan-participant/tpa-member-number-p1
            :gravie.employer-program/is-javelina?],
           :output
           [:gravie.person/id-number]}
    #::pco{:op-name
           gravie.person.resolver/person->now-zoned,
           :input
           [:db-gravie.person/timezone],
           :output
           [:gravie.person/now-zoned]}
    #::pco{:op-name
           gravie.person.resolver/person->email,
           :input
           [:db-gravie.user/username
            :db-gravie.person/email],
           :output
           [:gravie.person/email]}
    #::pco{:op-name
           gravie.person.resolver/person-birth-date->age,
           :input
           [:db-gravie.person/birth-date],
           :output
           [:graive.person/age]}
    #::pco{:op-name
           gravie.person.resolver/person->full-name,
           :input
           [:db-gravie.person/first-name
            :db-gravie.person/last-name],
           :output
           [:gravie.person/full-name]}
    #::pco{:op-name
           gravie.person.resolver/member-external-id->person,
           :input
           [:gravie.member/external-id],
           :output
           [:db-gravie.person/bank-account-id
            :db-gravie.person/birth-date
            :db-gravie.person/class
            :db-gravie.person/email
            :db-gravie.person/external-id
            :db-gravie.person/first-name
            :db-gravie.person/hpa-member-number
            :db-gravie.person/id
            :db-gravie.person/last-name
            :db-gravie.person/phone
            :db-gravie.person/ssn
            :db-gravie.person/timezone
            :db-gravie.person/user-id
            :db-gravie.person/virtual-bank-account-id]}])

(def resolvers
  (->> resolvers-without-resolve
       (mapv (fn [config]
                 (assoc config
                        ::pco/resolve (fn [_env _args]
                                        (p.shape/query->shape-descriptor
                                         (::pco/output config))))))
       (mapv pco/resolver)))

(def pathom-env
  (-> {::p.error/lenient-mode? true}
      (pci/register resolvers)
      ((requiring-resolve 'com.wsscode.pathom.viz.ws-connector.pathom3/connect-env)
       {::pvc/parser-id ::eita-env})))

(defn -main
  [_]
  (println :>>>STARTING)
  (clojure.pprint/pprint
   (p.eql/process pathom-env
                  {:gravie.member/external-id "10"}
                  [:gravie.person/full-name]))
  (System/exit 0))

(comment

  ;; This works.
  (p.eql/process pathom-env
                 {:gravie.person/external-id "10"}
                 [:gravie.person/full-name])

  ;; This doesn't.
  (p.eql/process pathom-env
                 {:gravie.member/external-id "10"}
                 [:gravie.person/full-name])

  ;; But this does work.
  (p.eql/process pathom-env
                 {:gravie.member/external-id "10"}
                 [:gravie.person/full-name
                  :gravie.person/first-name])

  ())
