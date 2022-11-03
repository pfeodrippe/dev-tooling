(ns com.pfeodrippe.tooling.instrumentation
  (:require
   [clojure.string :as str]
   [clojure.test :as t]
   [com.pfeodrippe.tooling.clerk.portal :as tool.clerk.portal]
   [com.pfeodrippe.tooling.portal :as tool.portal]
   [com.wsscode.pathom.connect :as-alias pco]
   [nextjournal.clerk :as clerk]
   [nextjournal.clerk.viewer :as clerk.viewer]))

(defn find-vars
  "Find vars in your project according to one or more of the following queries:
  - `:namespaces` (collection of namespaces in symbol or string format)
  - `:ns-meta` (namespaces which contain this metadata)
  - `:ns-prefix` (namespaces with this prefix)
  - `:vars` (collection of vars in symbol or string format)
  - `:var-meta` (vars which contain this metadata)"
  [{:keys [namespaces ns-meta ns-prefix vars var-meta]}]
  (when (or (seq namespaces) ns-meta (seq ns-prefix) (seq vars) var-meta)
    (let [namespaces-set (set (mapv str namespaces))
          vars-set (set (mapv (comp str symbol) vars))]
      (cond->> (all-ns)
        (seq ns-prefix)  (filter #(str/starts-with? % ns-prefix))
        (seq namespaces) (filter #(contains? namespaces-set (str %)))
        ns-meta          (filter #(-> % meta ns-meta))
        true             (mapv ns-interns)
        true             (mapcat vals)
        (seq vars)       (filter #(contains? vars-set (str (symbol %))))
        var-meta         (filter #(-> % meta var-meta))))))

(defonce *var->info (atom {}))

(defn instrument!
  "Instrument vars of your choice.

  `:mode` can be `:instrument` to instrument or `:unstrument` to remove the
  instrumentation.

  See `find-vars`."
  [vars {:keys [mode]}]
  (doseq [v vars]
    (case mode
      :instrument
      (cond
        ;; Check if it's a Pathom resolver.
        (::pco/resolve @v)
        (let [original-fn (::pco/resolve @v)
              input-output* (atom [])]
          (when (not (::input-output (meta v)))
            (alter-meta! v assoc
                         ::original-fn original-fn
                         ::input-output input-output*
                         ::original-meta (meta v))
            (alter-var-root v assoc ::pco/resolve (fn [env input]
                                                    (let [value (original-fn env input)]
                                                      (swap! *var->info update-in [v :input-output]
                                                             (comp vec conj)
                                                             (merge {:input input
                                                                     :output value}
                                                                    (when (seq t/*testing-vars*)
                                                                      {:testing-contexts (reverse t/*testing-contexts*)
                                                                       :testing-vars t/*testing-vars*})))
                                                      (swap! input-output* conj {:input input
                                                                                 :output value})
                                                      value)))))

        :else
        (let [original-fn (or (::original-fn (meta v))
                              (deref v))
              input-output* (atom [])]
          (when (and (not (::input-output (meta v)))
                     (fn? original-fn))
            (alter-meta! v assoc
                         ::original-fn original-fn
                         ::input-output input-output*
                         ::original-meta (meta v))
            (alter-var-root v (constantly (fn [& args]
                                            (let [value (apply original-fn args)]
                                              (swap! *var->info update-in [v :input-output]
                                                     (comp vec conj)
                                                     (merge {:input args
                                                             :output value}
                                                            (when (seq t/*testing-vars*)
                                                              {:testing-contexts (reverse t/*testing-contexts*)
                                                               :testing-vars t/*testing-vars*})))
                                              (swap! input-output* conj {:input args
                                                                         :output value})
                                              value)))))))

      :unstrument
      (cond
        (::pco/resolve @v)
        (when-let [original-fn (::original-fn (meta v))]
          (when (:doc (::original-meta (meta v)))
            (alter-meta! v assoc :doc (:doc (::original-meta (meta v)))))
          (alter-meta! v dissoc ::original-fn ::input-output ::original-meta :malli/schema)
          (alter-var-root v assoc ::pco/resolve original-fn))

        :else
        (when-let [original-fn (::original-fn (meta v))]
          (when (:doc (::original-meta (meta v)))
            (alter-meta! v assoc :doc (:doc (::original-meta (meta v)))))
          (alter-meta! v dissoc ::original-fn ::input-output ::original-meta :malli/schema)
          (alter-var-root v (constantly original-fn)))))))

(defn get-var-info
  [v]
  (get @*var->info v))

(def ^:private var-viewer
  "This viewer shows instrumented data (if available), otherwise acts as a
  normal var viewer."
  {:name ::var-viewer
   :pred clerk.viewer/var-from-def?
   :transform-fn (clerk.viewer/update-val
                  (fn [v]
                    (let [f ((comp deref ::clerk/var-from-def) v)]
                      (if-some [{:keys [input-output]} (get-var-info (::clerk/var-from-def v))]
                        (clerk.viewer/with-viewer tool.clerk.portal/portal-viewer
                          (-> (mapv #(-> %
                                         (select-keys [:input :output])
                                         (update :input tool.portal/tree)
                                         (update :output tool.portal/tree)
                                         (vary-meta merge
                                                    (when-let [testing-var (first (:testing-vars %))]
                                                      {:portal.runtime/type testing-var})))
                                    input-output)
                              (vary-meta merge
                                         {:portal.runtime/type (::clerk/var-from-def v)})))
                        f))))})

(def additional-viewers
  [var-viewer])

(def ^:private updated-viewers
  (clerk.viewer/add-viewers
   (->> (clerk.viewer/get-default-viewers)
        ;; Remove viewers with the same name so you don't have
        ;; duplicated ones.
        (remove (comp (set (mapv :name additional-viewers))
                      :name)))
   additional-viewers))

(clerk.viewer/reset-viewers! :default updated-viewers)
