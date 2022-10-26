(ns com.pfeodrippe.tooling.pathom
  "Most of the code here was taken from Pathom3."
  (:require
   [com.wsscode.pathom.connect :as-alias pco]
   [edn-query-language.core :as eql]))

(comment

  (require '[lambdaisland.classpath.watch-deps :as watch-deps])
  (watch-deps/start! {:aliases [:dev :test]})

  ())

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

(defn data->shape-descriptor-shallow
  "Like data->shape-descriptor, but only at the root keys of the data."
  [data]
  (zipmap (keys data) (repeat {})))

(defn shape-params [shape-value params]
  (vary-meta shape-value assoc ::pco/params params))

(defn ast->shape-descriptor
  "Convert EQL AST to shape descriptor format."
  [ast]
  (reduce
   (fn [m {:keys [key type children params] :as node}]
     (if (identical? :union type)
       (let [unions (into [] (map ast->shape-descriptor) children)]
         (reduce merge-shapes m unions))
       (assoc m key (cond-> (ast->shape-descriptor node)
                      (seq params)
                      (shape-params params)))))
   {}
   (:children ast)))

(defn query->shape-descriptor
  "Convert pathom output format into shape descriptor format."
  [output]
  (ast->shape-descriptor (eql/query->ast output)))

(defn reachable-joins
  [env available-data]
  (->> (reachable-paths env available-data)
       (filter (comp seq val))
       (into (sorted-map))))

(defn analyze-attributes
  "Receive `attributes` list, which are the nodes in the Pathom used
  as a starting point to reach other attributes."
  [pathom attributes]
  (let [available-data (->> attributes
                            (mapv (fn [attr] [attr {}]))
                            (into {}))

        env
        (-> ((:pathom-env pathom)
             nil [::pco/indexes])
            ::pco/indexes)]
    {:env env
     :reachable-paths (reachable-paths env available-data)
     :reachable-joins (reachable-joins env available-data)}))

(defn attribute-reachable?
  "Discover which attributes are available, given an index and a data context."
  [env available-data attr]
  (contains? (reachable-attributes env available-data) attr))
