(ns com.pfeodrippe.tooling.clerk.parser
  (:require
   [nextjournal.clerk.viewer :as clerk.viewer]
   [nextjournal.markdown :as md]
   [nextjournal.markdown.parser :as md.parser]
   [fr.jeremyschoffen.prose.alpha.reader.core :as reader]
   [fr.jeremyschoffen.prose.alpha.eval.common :as eval-common]
   [clojure.string :as str]
   [clojure.walk :as walk]))

(defn adapt-content
  [_opts content]
  (mapv (fn [el]
          (if (string? el)
            {:type :text
             :text el}
            el))
        content))

(defmulti prose->output
  "Converts a Prose tag to some format. If inexistent, `(str (list tag-name args))`
  will be used.

  It checks for `:output-format` and `:tag-name` keys, you can extend it to
  your prefered format using

  (defmethod prose->output [:md :my-tag]
    [_ & args]
    (str (list tag-name args)))"
  (fn [opts & _]
    ((juxt :output-format :tag-name) opts)))

;; == Built-in tags.
;; By default, a tag just returns a stringfied list.
(defmethod prose->output :default
  [{:keys [tag-name]} & args]
  (str (list tag-name args)))

(defmethod prose->output [:md :hard-break]
  [_opts & _args]
  ::hard-break)

(defmethod prose->output [:md :em]
  [opts & args]
  {:type :em
   :content (adapt-content opts args)})

(defmethod prose->output [:md :strong]
  [opts & args]
  {:type :strong
   :content (adapt-content opts args)})

(defmethod prose->output [:md :section]
  [opts & args]
  {:type :paragraph, :content (adapt-content opts args)})

(defmethod prose->output [:md :page-name]
  [opts & content]
  {:type :plain
   :content [{:type :heading
              :content (adapt-content opts content)
              :heading-level 1}]
   :toc [1 (adapt-content opts content)]})

(defmethod prose->output [:md :title]
  [opts & content]
  {:type :plain
   :content [{:type :heading
              :content (adapt-content opts content)
              :heading-level 2}]
   :toc [2 (adapt-content opts content)]})

(defmethod prose->output [:md :subtitle]
  [opts & content]
  {:type :plain

   :content [{:type :heading
              :content (adapt-content opts content)
              :heading-level 3}]
   :toc [3 (adapt-content opts content)]})

(defmethod prose->output [:md :link]
  [opts & content]
  {:type :link
   :content (adapt-content opts (if (= (count content) 1)
                                  content
                                  (drop 1 content)))
   :attrs {:href (first content)
           :target "_blank"}})

(defmethod prose->output [:md :command]
  [opts & content]
  {:type :monospace
   :content (adapt-content opts content)})

(defmethod prose->output [:md :code]
  [opts & content]
  {:type :monospace
   :content (adapt-content opts content)})

(defmethod prose->output [:md :todo-list]
  [opts & content]
  {:type :plain
   :content [{:type :em
              :content [{:type :text :text "TODO"}]}
             {:type :todo-list
              :content (adapt-content opts content)}]})

(defmethod prose->output [:md :todo-item]
  [{:keys [done] :as opts} & content]
  {:type :todo-item
   :content (adapt-content opts content)
   :attrs {:checked done}})

;; `:comment` hides the content. It's analogous to the comment macro
;; in Clojure.
(defmethod prose->output [:md :comment]
  [_opts & _content]
  {:type :text :text ""})

(defmethod prose->output [:md :numbered-list]
  [opts & content]
  {:type :numbered-list
   :content (->> content
                 (partition-by #{::hard-break})
                 (remove #{'(::hard-break)})
                 (mapv (fn [v]
                         {:type :list-item
                          :content (adapt-content opts v)}))
                 (adapt-content opts))})

#_(defmethod prose->output [:md :note]
  [opts & content]
  {:type :aside
   :content (adapt-content opts content)})

;; == Main
(defn prose-parser
  "Called when a keyword tag is found.

  E.g. `◊:my-keyword{my text}` would give us a `:tag-name` of `:my-keyword` and
  the text as args."
  [opts & args]
  (let [arg-maps (->> args
                      (take-while #(or (and (map? %)
                                            ;; Clojure args have metadata associated with them.
                                            (contains? (set (keys (meta %))) :row))
                                       (keyword? %)))
                      (map (fn [v]
                             ;; Just like Clojure metadata,
                             ;; convert keywords to maps with
                             ;; the field set to `true`.
                             (if (keyword? v)
                               {v true}
                               v))))
        result (apply prose->output
                      (merge (assoc opts :output-format :md)
                             ;; Apply maps from left to right.
                             (apply merge arg-maps))
                      ;; Remove maps that are being merged with `opts`.
                      (drop (count arg-maps) args))]
    (if (string? result)
      {:type :text
       :text result}
      result)))

(defn invoke-tag
  "It should be used to invoke tags.

  `args` should be a vector."
  ([tag args]
   (invoke-tag tag nil args))
  ([tag opts args]
   (apply prose-parser
          (merge {:tag-name tag} opts)
          args)))

(defn- eval-clojurized
  [match]
  (->> (eval-common/eval-forms match)
       (adapt-content {})))

(defn- auto-resolves [ns]
  (as-> (ns-aliases ns) $
    (assoc $ :current (ns-name *ns*))
    (zipmap (keys $)
            (map ns-name (vals $)))))

;; Override :tag dispatcher so we can collect all tags.
(def ^:dynamic **tags-collector* (atom []))

(defmethod reader/clojurize* :tag [node]
  (let [tag (->> node
                 :content
                 (into [] (mapcat reader/clojurize))
                 seq)]
    (swap! **tags-collector* conj tag)
    tag))

;; Override :tag-name dispatcher so we can dispatch on keywords.
(defmethod reader/clojurize* :tag-name [node]
  (let [node-value (-> node :content first reader/read-string*)]
    (cond
      (keyword? node-value)
      [`prose-parser {:tag-name node-value}]

      ;; If we have a symbol that does not resolve to var, make it
      ;; a keyword so it can be called as a tag.
      (and (symbol? node-value)
           (nil? (requiring-resolve (if (qualified-symbol? node-value)
                                      node-value
                                      (symbol (str *ns*) (name node-value))))))
      [`prose-parser {:tag-name (keyword node-value)}]

      :else
      [node-value])))

;; Alter main parser so we can accept Prose markup.
(alter-var-root #'md/parse
                (fn [_f]
                  (fn [markdown-text]
                    (let [parsed (reader/parse markdown-text)]
                      (if (and (= (:tag parsed) :doc)
                               (= (count (:content parsed)) 1))
                        ;; For the case where there is no `◊`, just parse the
                        ;; text as Markdown.
                        (-> markdown-text md/tokenize md.parser/parse)
                        {:type :prose-unevaluated
                         ;; Trim first character of each new line as they are just
                         ;; noise generated by the usage `;; ...`.
                         :content (-> (->> (str/split-lines markdown-text)
                                           (mapv #(if (= (first %) \space)
                                                    (subs % 1)
                                                    %))
                                           (str/join "\n"))
                                      ;; Hard breaks are used to build
                                      ;; paragraphs later.
                                      (str/replace #"\n\n" "◊:hard-break{}"))})))))

(defn- remove-hard-breaks
  [evaluated-result]
  (loop [[el & rest-list] evaluated-result
         acc []
         current-paragraph {:type :paragraph
                            :content []}]
    (if el
      (cond
        ;; If it's a ::hard-break, remove it and add
        ;; the current paragraph (if existent).
        (= el ::hard-break)
        (if (seq (:content current-paragraph))
          (recur rest-list
                 (conj acc current-paragraph)
                 (assoc current-paragraph :content []))
          (recur rest-list
                 acc
                 current-paragraph))

        ;; If it's a :plain, it means the it's some kind
        ;; of heading, we add the the current paragraph (if existent)
        ;; and then the plain element.
        (contains? #{:plain} (:type el))
        (if (seq (:content current-paragraph))
          (recur rest-list
                 (conj acc current-paragraph el)
                 (assoc current-paragraph :content []))
          (recur rest-list
                 (conj acc el)
                 current-paragraph))

        ;; Otherwise, the element is part of a paragraph.
        :else
        (recur rest-list
               acc
               (update current-paragraph :content conj el)))
      ;; Convert any remaining ::hard-break
      ;; into empty strings.
      (walk/prewalk (fn [v]
                      (if (vector? v)
                        (vec (remove #{::hard-break} v))
                        v))
                    (if (seq (:content current-paragraph))
                      (conj acc current-paragraph)
                      acc)))))

(defn blocks->markdown [{:as doc :keys [ns]}]
  (let [updated-doc
        (-> doc
            (update :blocks
                    #(mapv (fn [block]
                             ;; Evaluate Prose strings, adding a `:toc` field so
                             ;; it can be used to build the table of contents.
                             (if (= (-> block :doc :type) :prose-unevaluated)
                               (let [*collector (atom [])
                                     content
                                     (binding [**tags-collector* *collector
                                               reader/*reader-options*
                                               (merge reader/*reader-options*
                                                      ;; `*ns` belongs to the evaluated
                                                      ;; namespace, here we auto-resolve
                                                      ;; our aliases, see
                                                      ;; https://github.com/borkdude/edamame#auto-resolve
                                                      {:auto-resolve (auto-resolves *ns*)})]
                                       (let [parsed (reader/parse (-> block :doc :content))
                                             result (->> (reader/clojurize parsed)
                                                         eval-clojurized
                                                         remove-hard-breaks)]
                                         result))]
                                 {:type :markdown
                                  :doc {:type :doc
                                        :content content}
                                  :toc (remove nil? (mapv :toc content))
                                  :tags @*collector})
                               block))
                           %)))

        ;; Create toc for Prose if Prose is used, otherwise keep the
        ;; existing one.
        toc (or (some->> (:blocks updated-doc)
                         (mapcat :toc)
                         seq
                         (remove nil?)
                         (reduce (fn [acc [level title]]
                                   ;; Store level count in metadata.
                                   (-> (vary-meta acc update [level :count] (fnil inc 0))
                                       ;; Compute path based on level.
                                       (update-in (->> (range 1 level)
                                                       (mapcat (fn [idx]
                                                                 [(or (some-> (get (meta acc) [idx :count])
                                                                              dec)
                                                                      0)
                                                                  :children]))
                                                       (concat [:children])
                                                       vec)
                                                  (comp vec conj)
                                                  {:type :toc
                                                   :content title
                                                   :heading-level level})))
                                 {:type :toc
                                  :children []}))
                (:toc updated-doc))]
    (-> updated-doc
        (select-keys [:blocks :toc :toc-visibility :title])
        (assoc :toc toc)
        (cond-> ns (assoc :scope (clerk.viewer/datafy-scope ns))))))

(def ^:private custom-css
  [:<>
   [:style {:type "text/css"}
    "
aside {
    width: 40%;
    padding-left: .5rem;
    margin-left: -330px;
    float: left;
    font-style: italic;
    color: #29627e;
}

aside > p {
    margin: .5rem;
}

p {
    font-family: 'Fira Sans', sans-serif;
    color: black;
}
"]])

(defn process-blocks [viewers doc]
  (let [updated-doc (blocks->markdown doc)]
    (def bbb updated-doc)
    (def aaa
      (-> updated-doc
          (update :blocks (partial into []
                                   (comp (mapcat (partial clerk.viewer/with-block-viewer doc))
                                         (map (comp clerk.viewer/process-wrapped-value
                                                    clerk.viewer/apply-viewers*
                                                    (partial clerk.viewer/ensure-wrapped-with-viewers viewers))))))
          #_(update :blocks #(vec
                              (concat
                               [(clerk.viewer/mark-presented
                                 (clerk.viewer/with-viewer {:name :html- :render-fn 'identity}
                                   custom-css))]
                               %)))))
    aaa))

(def notebook-viewer
  {:name :clerk/notebook
   :render-fn 'nextjournal.clerk.render/render-notebook
   :transform-fn (fn [{:as wrapped-value :nextjournal/keys [viewers]}]
                   (def wrapped-value wrapped-value)
                   (-> wrapped-value
                       (update :nextjournal/value (partial process-blocks viewers))
                       clerk.viewer/mark-presented))})

(def aside-viewer
  {:name :nextjournal.markdown/aside
   :transform-fn (clerk.viewer/into-markup [:aside])})

(defn update-child-viewers [f]
  (fn [viewer]
    (update viewer :transform-fn (fn [transform-fn]
                                   (fn [wrapped-value]
                                     (-> wrapped-value
                                         transform-fn
                                         (update :nextjournal/viewers f)))))))

(def ^:private updated-viewers
  (clerk.viewer/update-viewers
   (clerk.viewer/get-default-viewers)
   {(comp #{:clerk/notebook} :name)
    (constantly notebook-viewer)

    (comp #{:markdown} :name)
    (update-child-viewers #(clerk.viewer/add-viewers % [aside-viewer]))}))

(clerk.viewer/reset-viewers! :default updated-viewers)

;; TODO:
;; - [x] Return sexp if function inexistent
;; - [x] Create multimethod for keywords
;; - [x] Add global viewer
;; - [x] Fix tokenizer for new lines
;; - [x] Make namespaced symbols work (don't eval while parsing)
;; - [x] Make functions look for symbols on the right namespace
;; - [x] Make `::` work
;; - [x] Make ToC minimally work
;; - [x] Collect tags
;; - [x] Check if things are working when publishing
;; - [x] Modify Portal notebook to use Prose
;; - [x] Add `p` to "standalone" paragraphs
;; - [x] Handle multiple lines like Markdown
;; - [ ] Collect commands
;; - [ ] Collect code
;; - [ ] Create view at the bottom with commands + code used
;;       just to show what's possible (or maybe a Recap section?)
;; - [ ] Ability to hide tags
;; - [ ] Add ability to query tags?
;;   - [ ] Can we add the response to the "DB"?
;; - [x] Revisit ToC
;; - [ ] Create a helper function to call tags as functions
;; - [ ] Create a tag that receives a html output and render it
;;   - [ ] So it can be used for parsing tag calls from code

(comment

  (md/parse "## Example\nlook")

  (nextjournal.clerk.parser/parse-markdown-string {:doc? true} "## Aaa
- s
### Eee")

  (md/parse "## Aaa
- s
### Eee")

  ())
