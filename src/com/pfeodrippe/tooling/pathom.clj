(ns com.pfeodrippe.tooling.pathom
  (:require
   [clojure.set :as set]
   [clojure.walk :as walk]
   [com.pfeodrippe.tooling.pathom.timeline :as timeline]
   [com.wsscode.pathom3.connect.indexes :as pci]
   [com.wsscode.pathom3.connect.operation :as pco]
   [com.wsscode.pathom3.connect.planner :as pcp]
   [com.wsscode.pathom3.connect.runner :as pcr]
   [com.wsscode.pathom3.error :as p.error]
   [com.wsscode.pathom3.format.shape-descriptor :as p.shape]
   [com.wsscode.pathom3.interface.eql :as p.eql]
   [nextjournal.clerk :as clerk]
   [nextjournal.clerk.viewer :as clerk.viewer]))

(defn reachable-attributes-for-groups*
  [{::pco/keys [index-io]} groups attributes]
  (into #{}
        (comp (filter #(every? (fn [x] (contains? attributes x)) %))
              (mapcat #(-> index-io (get %) keys))
              (remove #(contains? attributes %)))
        groups))

(defn reachable-attributes*
  [{::pco/keys [index-io] :as env} queue attributes]
  (if (seq queue)
    (let [[attr & rest] queue
          attrs (conj! attributes attr)]
      (recur env
             (into rest (remove #(contains? attrs %)) (-> index-io (get #{attr}) keys))
             attrs))
    attributes))

(defn attrs-multi-deps
  [{::pco/keys [index-attributes]} attrs]
  (into #{} (mapcat #(get-in index-attributes [% ::pco/attr-combinations])) attrs))

(defn reachable-attributes
  "Discover which attributes are available, given an index and a data context.
  Also includes the attributes from available-data.

  Based on https://github.com/wilkerlucio/pathom3/blob/c456c7b5c8e0ee6e12a4b755cfb8b23fbfda8652/src/main/com/wsscode/pathom3/connect/indexes.cljc#L329."
  [{::pco/keys [index-io] :as env} available-data]
  (let [queue (-> #{}
                  (into (keys (get index-io #{})))
                  (into (keys available-data)))]
    (loop [attrs         (persistent! (reachable-attributes* env queue (transient #{})))
           group-reaches (reachable-attributes-for-groups* env (attrs-multi-deps env attrs) attrs)]
      (if (seq group-reaches)
        (let [new-attrs (persistent! (reachable-attributes* env group-reaches (transient attrs)))]
          (recur
           new-attrs
           (reachable-attributes-for-groups* env (attrs-multi-deps env new-attrs) new-attrs)))
        attrs))))

(defn merge-shapes
  "Deep merge of shapes, it takes in account that values are always maps."
  ([a] a)
  ([a b]
   (cond
     (and (map? a) (map? b))
     (with-meta (merge-with merge-shapes a b)
       (merge (meta a) (meta b)))

     (map? a) a
     (map? b) b

     :else b)))

(defn remove-keys
  [f m]
  (into (with-meta {} (meta m)) (remove (comp f key)) m))

(defn reachable-paths*
  [{::pco/keys [index-io] :as env} queue paths]
  (if (seq queue)
    (let [[[attr sub] & rest] queue
          attrs (update paths attr merge-shapes sub)]
      (recur env
             (into rest
                   (remove #(contains? attrs (key %)))
                   (get index-io #{attr}))
             attrs))
    paths))

(defn reachable-paths-for-groups*
  [{::pco/keys [index-io]} groups attributes]
  (->> (reduce
        (fn [group attr]
          (merge-shapes group (get index-io attr)))
        {}
        (filterv #(every? (fn [x] (contains? attributes x)) %) groups))
       (remove-keys #(contains? attributes %))))

(defn reachable-paths
  "Discover which paths are available, given an index and a data context.
  Also includes the attributes from available-data.

  From https://github.com/wilkerlucio/pathom3/blob/main/src/main/com/wsscode/pathom3/connect/indexes.cljc#L369."
  [{::pco/keys [index-io] :as env} available-data]
  (let [queue (merge-shapes (get index-io #{}) available-data)]
    (loop [paths         (reachable-paths* env queue {})
           group-reaches (reachable-paths-for-groups* env (attrs-multi-deps env (keys paths)) paths)]
      (if (seq group-reaches)
        (let [new-attrs (reachable-paths* env group-reaches paths)]
          (recur
           new-attrs
           (reachable-paths-for-groups* env (attrs-multi-deps env (keys new-attrs)) new-attrs)))
        (->> paths
             (remove #(contains? #{"com.wsscode.pathom.connect"
                                   "com.wsscode.pathom"
                                   "pathom.viz"}
                                 (namespace (first %))))
             (into (sorted-map)))))))

(defn shape-params [shape-value params]
  (vary-meta shape-value assoc ::pco/params params))

(defn- reachable-joins
  [env available-data]
  (->> (reachable-paths env available-data)
       (filter (comp seq val))
       (into (sorted-map))))

(defn analyze-attributes
  "Receive `attributes` list, which are the nodes in the Pathom used
  as a starting point to reach other attributes."
  [parser attributes]
  (let [available-data (->> attributes
                            (mapv (fn [attr] [attr {}]))
                            (into {}))
        env (-> (parser nil [::pco/indexes])
                ::pco/indexes)]
    {:env env
     :reachable-paths (reachable-paths env available-data)
     :reachable-joins (reachable-joins env available-data)}))

(defn attribute-reachable?
  "Discover which attributes are available, given an index and a data context."
  [env available-data attr]
  (contains? (reachable-attributes env available-data) attr))

;; Dependencies.
(defn- indexes-from-steps
  "Get first step where all the attributes appear in the output."
  [steps]
  (->> steps
       ;; Reverse so an earlier step can override a later one.
       reverse
       (mapv ::pcr/node-resolver-output)
       (reduce (fn [{:keys [idx] :as acc} resolver-output]
                 (-> acc
                     (update :idx dec)
                     (update :indexes merge (zipmap (keys resolver-output) (repeat idx)))))
               {:idx (dec (count steps))
                :indexes {}})
       :indexes))

(defn steps-from-attr
  [steps attr->idx attr]
  (flatten
   (when-let [current-idx (get attr->idx attr)]
     (let [inputs (->> current-idx (get steps) ::pcp/input keys)]
       (conj [{:idx current-idx
               :attr-inputs {attr (set inputs)}}]
             (->> inputs
                  (mapv #(steps-from-attr steps attr->idx %))))))))

(defn adapt-timeline
  [timeline]
  (walk/prewalk (fn [v]
                  (cond
                    (volatile? v)
                    (deref v)

                    ;; Just truncate error messages as they slow down Emacs
                    ;;pretty-printing.
                    (and (map-entry? v)
                         (contains? #{ ::p.error/error-message ::p.error/error-stack} (key v)))
                    (clojure.lang.MapEntry. (key v) (subs (val v) 0 50))

                    :else
                    v))
                timeline))

(defn timeline-from-pathom-result
  [pathom-result]
  (let [timeline (-> pathom-result
                     timeline/response-trace
                     adapt-timeline)
        collector (atom [])]
    (walk/prewalk (fn [v]
                    (if (:path v)
                      (do (swap! collector conj (dissoc v :children))
                          v)
                      v))
                  timeline)
    (->> @collector
         (filter (comp seq :path))
         (filter #(or (= (:event (first (:details %))) "Run resolver")
                      (and (= (:event (first (:details %))) "Make plan")
                           (::pcp/unreachable-paths (:run-stats %))))))))

(defn path-prefix-steps-from-timeline
  [timeline]
  (->> timeline
       (mapv #(let [{::pcp/keys [user-request-shape nodes _index-attrs unreachable-paths]
                     ::pcr/keys [node-run-stats]}
                    (:run-stats %)

                    path (:path %)
                    node-id (last path)
                    {::pcr/keys [node-resolver-output
                                 node-resolver-input]}
                    (get node-run-stats node-id)]
                (-> {:path path
                     ::pco/op-name (::pco/op-name (get nodes node-id))
                     ::pcp/user-request-shape user-request-shape
                     ::pcp/input (::pcp/input (get nodes node-id))
                     ::pcr/node-resolver-input node-resolver-input
                     ::pcr/node-resolver-output node-resolver-output}
                    (merge (when unreachable-paths
                             {::pcp/unreachable-paths unreachable-paths})))))
       (group-by (fn [{:keys [path]
                       ::pco/keys [op-name]}]
                   ;; If no `op-name`, then there is no node associated.
                   (if op-name
                     (vec (drop-last path))
                     path)))
       (into (sorted-map))))

(defn- concatv
  [& colls]
  (apply (comp vec concat) colls))

(defn attr-dependencies-from-pathom-result
  [pathom-result]
  (let [timeline (timeline-from-pathom-result pathom-result)
        path-prefix->steps (path-prefix-steps-from-timeline timeline)
        run-stats (-> pathom-result meta ::pcr/run-stats)
        root-unreachable-paths (set (keys (::pcp/unreachable-paths run-stats)))
        pathom-eql-shape (-> run-stats ::pcp/user-request-shape)
        #_ #__ (do (def timeline timeline)
                   (def path-prefix->steps path-prefix->steps)
                   (def pathom-result pathom-result))
        transform-eql
        (fn transform-eql
          ([path-prefix eql-shape]
           (transform-eql path-prefix eql-shape {}))
          ([path-prefix eql-shape {:keys [unreachable-paths
                                          parent-unreachable]}]
           (let [steps (get path-prefix->steps path-prefix)
                 attr->output (->> (mapv ::pcr/node-resolver-output steps)
                                   (apply merge))
                 indexes (indexes-from-steps steps)]
             (->> eql-shape
                  (mapv (fn [[attr nested-shape]]
                          (let [attr-steps (steps-from-attr steps indexes attr)
                                resolver-calls
                                (->> attr-steps
                                     (mapv (fn [{:keys [idx]}] (::pco/op-name (get steps idx))))
                                     reverse
                                     vec)

                                attr-dependencies
                                (->> attr-steps
                                     (mapv :attr-inputs)
                                     reverse
                                     vec)

                                resolver-attr-dependencies (mapv vector resolver-calls attr-dependencies)
                                resolver-subqueries
                                (->> attr-steps
                                     (mapv (fn [{:keys [idx]}] (get steps idx)))
                                     reverse
                                     (mapv (fn [{::pcp/keys [input]}]
                                             (when-let [nested-inputs (->> input
                                                                           (filter (comp seq val))
                                                                           seq)]
                                               (->> nested-inputs
                                                    (mapcat #(transform-eql (conj path-prefix (first %))
                                                                            (last %)))
                                                    vec))))
                                     (mapv vector resolver-calls))

                                output (get attr->output attr)
                                unreachable (or (contains? (set/union
                                                            (set (mapcat (comp keys ::pcp/unreachable-paths) steps))
                                                            unreachable-paths)
                                                           attr)
                                                parent-unreachable)
                                merge-properties
                                (fn merge-properties
                                  [v]
                                  (cond-> v
                                    (:resolver-attr-dependencies v)
                                    (assoc :resolver-attr-dependencies
                                           (concatv resolver-attr-dependencies (:resolver-attr-dependencies v))
                                           :resolver-subqueries
                                           (concatv resolver-subqueries (:resolver-subqueries v))
                                           :resolver-calls
                                           (concatv resolver-calls (:resolver-calls v)))))]
                            ;; We want to flatten everything for displaying, so  we just concat
                            ;; the children.
                            (concat
                             [(merge {:path (conj path-prefix attr)}
                                     (if unreachable
                                       {:error :unreachable}
                                       (merge
                                        {:resolver-calls resolver-calls
                                         :resolver-attr-dependencies resolver-attr-dependencies
                                         :resolver-subqueries resolver-subqueries})))]
                             (if (sequential? output)
                               (->> output
                                    (map-indexed
                                     (fn [idx _out]
                                       (->> (transform-eql (conj path-prefix attr idx)
                                                           nested-shape
                                                           {:parent-unreachable unreachable})
                                            (mapv merge-properties))))
                                    vec)
                               (->> (transform-eql (conj path-prefix attr)
                                                   nested-shape
                                                   {:parent-unreachable unreachable})
                                    (mapv merge-properties)))))))
                  flatten
                  (sort-by :path)
                  (remove (fn [attr-dep]
                            (and (-> attr-dep :path last keyword?)
                                 (= (-> attr-dep :path last namespace) ">"))))
                  (mapv (fn [attr-dep]
                          (if (every? (comp nil? last) (:resolver-subqueries attr-dep))
                            (dissoc attr-dep :resolver-subqueries)
                            attr-dep)))
                  vec))))]
    ;; We may not have a timeline available if everything is unreachable, so
    ;; we pass the root unreachable paths here.
    (transform-eql [] pathom-eql-shape {:unreachable-paths root-unreachable-paths})))

(defn mock-pathom-env
  [pathom-env]
  (update pathom-env
          ::pci/index-resolvers
          (fn [index-resolvers]
            (->> index-resolvers
                 (mapv (fn [[resolver-name {:keys [resolve config] :as resolver}]]
                         [resolver-name
                          (-> resolver
                              (assoc :resolve
                                     (with-meta
                                       (fn [_env _args]
                                         (p.shape/query->shape-descriptor
                                          (::pco/output config)))
                                       (meta resolve)))
                              (update-in [:config ::pco/op-name] with-meta resolver))]))
                 (into {})))))

(defn process-query
  "Given a pathom env, an entity and a EQL query, modify the resolvers
  so they always return their outputs.

  This can be used to see how a query using static information."
  [pathom-env entity eql-query]
  (let [pathom-env (mock-pathom-env pathom-env)
        pathom-result (p.eql/process pathom-env entity eql-query)]
    (with-meta (attr-dependencies-from-pathom-result pathom-result)
      {:type `processed-query
       :pathom-result pathom-result
       :entity entity
       :eql-query eql-query})))

(def ^:private shape-descriptor-viewer-render-fn
  '(fn [{:keys [shape path->info]}]
     (reagent/with-let [!state (reagent/atom {})]
       (let [shape->hiccup
             (fn shape->hiccup
               [{:keys [shape level path-prefix]
                 :or {level 0
                      path-prefix []}}]
               (into [:<>]
                     (->> shape
                          (sort-by first)
                          (map (fn [[attr v]]
                                 (let [path (conj path-prefix attr)
                                       {:keys [resolver-attr-dependencies error]} (get path->info path)
                                       attr-dependencies
                                       (->> resolver-attr-dependencies
                                            (mapv (fn [[resolver deps]]
                                                    {:resolver resolver
                                                     :attr-output (ffirst deps)
                                                     :attr-deps (last (first deps))})))]
                                   [:div.mt-1
                                    [:div {:class (-> (if error
                                                        [:bg-red-600
                                                         :text-white
                                                         :hover:bg-red-900]
                                                        (conj [(nth [:bg-blue-100
                                                                     :bg-yellow-100
                                                                     :bg-green-100
                                                                     :bg-purple-100]
                                                                    (mod level 4))]
                                                              (nth [:hover:bg-blue-200
                                                                    :hover:bg-yellow-200
                                                                    :hover:bg-green-200
                                                                    :hover:bg-purple-200]
                                                                   (mod level 4))))
                                                      (conj :cursor-pointer))
                                           :on-click #(swap! !state update-in
                                                             [path :expanded]
                                                             not)}
                                     [:div.p-1
                                      (if (get-in @!state [path :expanded])
                                        (cond
                                          error
                                          (if (= error :unreachable)
                                            "No resolvers were found to satisfy this attribute"
                                            (str error))

                                          (= (namespace attr) ">")
                                          [:<>
                                           "This is a "
                                           [:a {:href "https://pathom3.wsscode.com/docs/placeholders"
                                                :target :_blank}
                                            "placeholder"]]

                                          (empty? attr-dependencies)
                                          "Data was already available with the initial context =D"

                                          :else
                                          (into [:<>]
                                                (->> attr-dependencies
                                                     (mapv (fn [{:keys [resolver attr-output attr-deps]}]
                                                             [:div.text-sm
                                                              [:span.text-emerald-800.font-mono (str resolver)]
                                                              [:div.ml-2.mb-2.text-xs
                                                               "It resolves "
                                                               [:span.text-fuchsia-700.font-mono
                                                                (str attr-output)]
                                                               ","
                                                               [:br]
                                                               " which depends on "
                                                               [:span.text-fuchsia-700.font-mono
                                                                (str attr-deps)]]])))))
                                        (str attr))]]
                                    (when (seq v)
                                      [:div.ml-6
                                       [shape->hiccup {:shape v
                                                       :level (inc level)
                                                       ;; The path prefix for a join
                                                       ;; is the path of the parent
                                                       ;; (assuming that the output
                                                       ;; is a map, ofc, which in
                                                       ;; our case it's true).
                                                       :path-prefix path}]])])))
                          vec)))]
         [shape->hiccup {:shape shape}]))))

(def query-viewer
  {:name ::query-viewer
   :pred #(= (type %) `processed-query)
   :transform-fn
   (comp clerk/mark-presented
         (clerk/update-val (fn [value]
                             (let [{:keys [eql-query]} (meta value)]
                               {:shape (p.shape/query->shape-descriptor eql-query)
                                :path->info (->> value
                                                 (mapv (juxt :path identity))
                                                 (into {}))}))))

   :render-fn shape-descriptor-viewer-render-fn})

(def additional-viewers
  [query-viewer])

(def ^:private updated-viewers
  (clerk.viewer/add-viewers
   (->> (clerk.viewer/get-default-viewers)
        ;; Remove viewers with the same name so you don't have
        ;; duplicated ones.
        (remove (comp (set (mapv :name additional-viewers))
                      :name)))
   additional-viewers))

(clerk.viewer/reset-viewers! :default updated-viewers)

(comment

  ;; TODO:
  ;; - [x] Create an index from the attrs to the last step where the attr appears
  ;; - [x] Walk over each attribute in pathom result and tell us where this did come from
  ;; - [x] Show unreacheable paths
  ;; - [x] See how it works with nested inputs
  ;; - [x] Fix representation for vectors
  ;; - [x] Fix representation for attributes that do not need a node
  ;; - [x] Also show the attribute dependencies in addition to the resolver dependencies
  ;; - [x] Fix nested inputs
  ;;   - [x] Walk over `::pcp/input` (shape descriptor) to process subqueries
  ;;   - [x] Check vectors
  ;; - [ ] Present it with clerk
  ;;   - [x] Present EQL
  ;;   - [x] Show info about a EQL when you hover or click over an attribute
  ;;   - [x] Explain what a placeholder is
  ;;   - [x] Resolver deps
  ;;   - [x] Attr deps
  ;;   - [x] Fix query processing, the timeline is not being shown properly
  ;;   - [x] Fix portal notebook
  ;;   - [ ] Fix attr datafy
  ;;   - [ ] Check if we can present a pathom result that was run in conjunction
  ;;         with a test
  ;;   - [ ] Statically, find some way to tag a query + initial data so a viewer can
  ;;         be shown for it
  ;; - [ ] Warn if there are more than 1 way to get the same output in a timeline?
  ;; - [ ] Show in which EQL queries an attribute is used

  ())

;; TODO:
;; - [ ] Add Malli types to some public functions
;; - [ ] Improve speed for big queries
;; - [ ] ...
