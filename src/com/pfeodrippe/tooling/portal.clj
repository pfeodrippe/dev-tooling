(ns com.pfeodrippe.tooling.portal
  (:require
   [clojure.core.protocols :as protocols]
   [clojure.datafy :as datafy]
   [clojure.edn :as edn]
   [clojure.string :as str]
   [clojure.walk :as walk]
   [com.wsscode.pathom.connect :as-alias pco]
   [com.pfeodrippe.tooling.pathom :as tool.pathom]
   [hiccup.core :as hiccup]
   [malli.core :as m]
   [malli.generator :as mg]
   [portal.api :as portal]
   [portal.runtime :as portal-rt]
   [taoensso.encore :as enc]
   [taoensso.timbre :as timbre]))

(defmacro eval-portal-cljs
  [& forms]
  `(portal/eval-str
    ~(pr-str (concat '(do) forms))))

;; ## Logging
(defonce *portal-logs (atom nil))

(defn- str-join
  [xs]
  (enc/str-join " "
                (map
                 (fn [x]
                   (let [x (enc/nil->str x)] ; Undefined, nil -> "nil"
                     (if (record? x)
                       (pr-str x)
                       x))))
                xs))

(defn log->portal [{:keys [level ?err msg_ timestamp_ ?ns-str ?file context ?line vargs_]}]
  (let [last-varg (try
                    (-> vargs_ force last)
                    (catch Exception e
                      (str e)))
        structured-data? (map? last-varg)]
    (merge
     (when ?err
       {:error (datafy/datafy ?err)})
     (when-let [ts (force timestamp_)]
       {:time ts})
     {:level   level
      :ns      (symbol (or ?ns-str ?file "?"))
      :line    (or ?line 1)
      :column  1
      :msg     (if structured-data?
                 (try
                   (-> vargs_ force drop-last str-join)
                   (catch Exception _ msg_))
                 (force msg_))
      :runtime :clj}
     (when structured-data?
       {:args last-varg})
     context)))

(defonce ^:private *logs (atom '()))

(defn- log
  "Accumulate a rolling log of 100 entries."
  [log]
  (swap! *logs
         (fn [logs]
           (take 100 (conj logs (log->portal log))))))

(defn setup-portal-logs []
  (or @*portal-logs
      (do
        (reset! *logs '())
        (timbre/merge-config!
         {:appenders
          {:memory {:enabled? true :fn log}}})
        (reset! *portal-logs
                (portal/open {:window-title "Logs Viewer"
                              :value *logs
                              :launcher false})))))

;; ## Helper functions.
(defn pprint
  [v]
  (cond
    (instance? clojure.lang.IMeta v)
    (vary-meta v merge
               {:portal.viewer/default :portal.viewer/pprint})

    (map-entry? v)
    (with-meta (into [] v)
      {:portal.viewer/default :portal.viewer/pprint})

    :else
    v))

(defn markdown
  [v]
  (cond
    (instance? clojure.lang.IMeta v)
    (vary-meta v merge
               {:portal.viewer/default :portal.viewer/markdown})

    (map-entry? v)
    (with-meta (into [] v)
      {:portal.viewer/default :portal.viewer/markdown})

    (string? v)
    (with-meta [:portal.viewer/markdown v]
      {:portal.viewer/default :portal.viewer/hiccup})

    :else
    v))

(defn hiccup
  [v]
  (cond
    (instance? clojure.lang.IMeta v)
    (vary-meta v merge
               {:portal.viewer/default :portal.viewer/hiccup})

    (map-entry? v)
    (with-meta (into [] v)
      {:portal.viewer/default :portal.viewer/hiccup})

    (string? v)
    (with-meta [:portal.viewer/hiccup v]
      {:portal.viewer/default :portal.viewer/hiccup})

    :else
    v))

(defn tree
  [v]
  (cond
    (instance? clojure.lang.IMeta v)
    (vary-meta v merge
               {:portal.viewer/default :portal.viewer/tree})

    (map-entry? v)
    (with-meta (into [] v)
      {:portal.viewer/default :portal.viewer/tree})

    (string? v)
    (with-meta [:portal.viewer/tree v]
      {:portal.viewer/default :portal.viewer/hiccup})

    :else
    v))

(defn inspector
  [v]
  (cond
    (instance? clojure.lang.IMeta v)
    (vary-meta v merge
               {:portal.viewer/default :portal.viewer/inspector})

    (map-entry? v)
    (with-meta (into [] v)
      {:portal.viewer/default :portal.viewer/inspector})

    (string? v)
    (with-meta [:portal.viewer/inspector v]
      {:portal.viewer/default :portal.viewer/hiccup})

    :else
    v))

(defn html
  [v]
  (with-meta [:portal.viewer/html (hiccup/html v)]
    {:portal.viewer/default :portal.viewer/hiccup}))

(defn exercise-schema
  "Exercise a schema, returning Malli generated samples. Adds a `nav` to it so
  you can generate it again by navigating into the `:malli/generated` keyword."
  [schema]
  (with-meta {:malli/generated (pprint (mg/sample (m/schema schema)))}
    {`protocols/nav (fn [_coll k v]
                      (if (and (nil? k) (= v :malli/generated))
                        (exercise-schema schema)
                        v))}))

(portal-rt/register! #'exercise-schema
                     {:predicate m/schema
                      :name `exercise-schema})

(defn datafy-keywords
  [parser]
  (extend-protocol protocols/Datafiable
    clojure.lang.Keyword
    (datafy [v]
      (or (let [schema
                (try (some-> (m/deref-all v)
                             pr-str
                             edn/read-string
                             pprint)
                     (catch Exception _))

                {:keys [reachable-paths reachable-joins env]}
                (-> (tool.pathom/analyze-attributes parser [v]))

                global?
                #(->> env ::pco/index-attributes % ::pco/attr-reach-via
                      keys
                      (every? empty?))]
            (when (-> env ::pco/index-attributes v)
              (let [namespace->reachable-paths (->> reachable-paths
                                                    (group-by (comp namespace first))
                                                    (mapv (fn [[k v]]
                                                            [k (into {} v)]))
                                                    (into {}))
                    nav-attributes (fn [_coll _k v]
                                     (with-meta (->> (namespace->reachable-paths (str v))
                                                     (mapv first)
                                                     sort)
                                       {`protocols/nav (fn [_coll _k v]
                                                         (datafy/datafy v))}))
                    grouped-reachable-joins (->> reachable-joins
                                                 (mapv (fn [[k v]]
                                                         (let [grouped-v
                                                               (->> (-> (tool.pathom/analyze-attributes parser (keys v))
                                                                        :reachable-paths
                                                                        keys
                                                                        sort)
                                                                    (remove global?)
                                                                    (group-by (comp namespace))
                                                                    (remove (comp nil? first))
                                                                    (mapv (fn [[k v]]
                                                                            [(symbol k) (sort v)]))
                                                                    (into (sorted-map)))]
                                                           [k (with-meta (sort (keys grouped-v))
                                                                {:portal.runtime/type 'NAVIGABLE_NAMESPACES
                                                                 `protocols/nav (fn [_coll _k v]
                                                                                  (with-meta (->> (grouped-v v)
                                                                                                  sort)
                                                                                    {`protocols/nav (fn [_coll _k v]
                                                                                                      (datafy/datafy v))}))})])))
                                                 (into (sorted-map)))]
                (with-meta {:schema (when schema
                                      (with-meta (merge {:malli/schema schema}
                                                        (m/properties (m/deref (m/schema v))))
                                        {`protocols/nav (fn [_coll k v]
                                                          (if (= k :malli/schema)
                                                            (exercise-schema v)
                                                            (datafy/datafy v)))}))
                            ;; Create navigable namespaces.
                            :namespaces (with-meta (->> namespace->reachable-paths
                                                        (remove #(every? global? (keys (last %))))
                                                        keys
                                                        sort
                                                        (mapv symbol))
                                          {:portal.runtime/type 'NAVIGABLE_NAMESPACES
                                           `protocols/nav nav-attributes})
                            :joins (with-meta (->> grouped-reachable-joins
                                                   (remove (comp global? first))
                                                   (into (sorted-map)))
                                     {:portal.runtime/type 'NAVIGABLE_JOINS
                                      `protocols/nav (fn [_coll _k v]
                                                       (datafy/datafy v))})
                            :global-namespaces (with-meta (->> namespace->reachable-paths
                                                               (filter #(every? global? (keys (last %))))
                                                               keys
                                                               sort
                                                               (mapv symbol))
                                                 {:portal.runtime/type 'GLOBAL_NAMESPACES
                                                  `protocols/nav nav-attributes})}
                  {::type :keyword
                   :portal.runtime/type v}))))
          v))))

(defn spec-exercise
  [_]
  nil)

;; Disable spec exercise as we don't use spec here.
(portal-rt/register! #'spec-exercise
                     {:predicate (constantly false)
                      :name 'clojure.spec.alpha/exercise})

(defn process-query
  [parser entity query]
  (let [result (-> (parser {} [{(list (first entity) {:pathom/context (into {} (rest entity))})
                                query}])
                   (update :com.wsscode.pathom/trace
                           (fn [trace]
                             (walk/postwalk
                              (fn [v]
                                (if (:path v)
                                  (-> (select-keys v [:path :details :children])
                                      (update :details
                                              (fn [details]
                                                (->> details
                                                     (filter #(= (:event %) "call-resolver"))
                                                     (mapv #(select-keys % [:key :sym]))
                                                     (mapv #(if (::pco/alias? (meta (:sym %)))
                                                              {:sym {:alias {(first (::pco/input (meta (:sym %))))
                                                                             (first (::pco/output (meta (:sym %))))}}}
                                                              %))
                                                     ;; Show details as a chain of resolvers.
                                                     (mapv :sym)))))
                                  v))
                              trace))))]
    (merge result
           {:entity entity
            :query query})))

(defn- adapt-detail
  [detail]
  (if (:alias detail)
    (format "`%s` -> `%s`"
            (key (first (:alias detail)))
            (val (first (:alias detail))))
    (format "`%s`" detail)))

(defn trace-event->markdown
  [pathom-result event]
  (let [current-attr (last (:path event))
        details (:details event)
        first-detail (first details)
        output (get-in pathom-result (:path event))]
    (cond
      (and (= (count details) 1)
           (map? first-detail)
           (contains? first-detail :alias))
      (format "To resolve `%s`, we just need one alias:\n%s"
              current-attr
              (->> details
                   (mapv #(str "- " (adapt-detail %)))
                   (str/join "\n")))

      (seq details)
      (format "To resolve `%s`, the following has happened in sequence:\n%s"
              current-attr
              (->> details
                   (mapv #(str "1. " (adapt-detail %)))
                   (str/join "\n")))

      (and (keyword? current-attr)
           (= (namespace current-attr) ">"))
      (format "`%s` is a [placeholder](https://blog.wsscode.com/pathom/v2/pathom/2.2.0/core/placeholders.html), it's used to build the shape of the response"
              current-attr)

      (= (count (:path event)) 1)
      (format "`%s` is the query, so no resolvers were needed for it as this information already exists"
              current-attr)

      (and (keyword? output)
           (= (namespace output) "com.wsscode.pathom.core"))
      (format "## Error
Tried to resolve `%s`, but wasn't successful
```clojure
%s
```"
              current-attr
              output)

      :else
      (format "The information for `%s` already exists in the cache, it was resolved by another attribute"
              current-attr))))

(defn analyze-pathom-result
  [{:keys [com.wsscode.pathom/trace entity query] :as pathom-result}]
  (let [collector (atom [])]
    (walk/prewalk (fn [v]
                    (if (:path v)
                      (do (swap! collector conj (dissoc v :children))
                          v)
                      v))
                  trace)
    (let [output (get pathom-result (first entity))]
      (-> (->> @collector
               (filter (comp seq :path))
               (remove (comp #{[:com.wsscode.pathom/trace]} :path))
               (mapv #(let [result (get-in pathom-result (:path %))]
                        (-> %
                            (assoc :description (markdown (trace-event->markdown pathom-result %)))
                            (dissoc :path :details)
                            (with-meta {:portal.runtime/type (pprint (:path %))
                                        :original-value (pprint %)})
                            (merge (when (and (keyword? result)
                                              (= (namespace result) "com.wsscode.pathom.core"))
                                     {:error result}))))))
          (with-meta {:entity entity
                      :query query
                      :output (pprint output)
                      :portal.runtime/type :PATHOM_QUERY})))))

(defn attribute
  "Get information about this attribute."
  [v]
  (datafy/datafy v))

(portal-rt/register! #'attribute
                     {:predicate #(and (qualified-keyword? %)
                                       (::type (meta (attribute %))))
                      :name `attribute})

(defn clear
  []
  (portal/clear))
