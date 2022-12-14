(ns com.pfeodrippe.tooling.clerk.parser
  (:require
   [nextjournal.clerk.classpath :as cp]
   [babashka.fs :as fs]
   [nextjournal.clerk.viewer :as clerk.viewer]
   [nextjournal.markdown :as md]
   [nextjournal.clerk.parser :as clerk.parser]
   [nextjournal.markdown.parser :as md.parser]
   [clojure.java.io :as io]
   [fr.jeremyschoffen.prose.alpha.reader.core :as reader]
   [fr.jeremyschoffen.prose.alpha.eval.common :as eval-common]
   [clojure.string :as str]
   [clojure.walk :as walk]
   [nextjournal.clerk.webserver :as clerk.webserver]
   [org.httpkit.server :as httpkit]
   [clojure.pprint :as pp]
   [nextjournal.clerk :as clerk]
   [nextjournal.clerk.view :as clerk.view]
   [nextjournal.clerk.analyzer :as clerk.analyzer]
   [nextjournal.clerk.builder :as clerk.builder]
   [com.pfeodrippe.tooling.clerk.var-changes :as var-changes]
   [clojure.main]))

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
        (or (contains? #{:plain} (:type el))
            (:no-paragraph el))
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
  [{:keys [subtitle]} & content]
  {:type :title-block
   :content [{:type :topic
              :text content
              :no-paragraph true}
             {:type :short-rule
              :text subtitle
              :no-paragraph true}]
   :no-paragraph true})

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

(def link-classes
  [:hover:bg-orange-100 :hover:rounded :hover:no-underline
   :transition :duration-200 :ease-in :ease-out])

(defmethod prose->output [:md :link]
  [opts & content]
  {:type :link
   :content (adapt-content opts (if (= (count content) 1)
                                  content
                                  (drop 1 content)))
   :attrs {:href (first content)
           :target "_blank"
           :external_link "true"
           :class link-classes}})

(defonce *state
  (atom {:path-info {}}))

(defn- ns->file [ns]
  (some (fn [dir]
          (some (fn [ext]
                  (let [path (str dir fs/file-separator (clerk.analyzer/ns->path ns) ext)]
                    (when (fs/exists? path)
                      path)))
                [".clj" ".cljc" ".md"]))
        (cp/classpath-directories)))

(defn- ns->jar [ns]
  (let [path (clerk.analyzer/ns->path ns)]
    (some #(when (or (.getJarEntry % (str path ".clj"))
                     (.getJarEntry % (str path ".cljc"))
                     (.getJarEntry % (str path ".md")))
             (.getName %))
          (cp/classpath-jarfiles))))

(defn- path->ns
  [path]
  (when path
    (let [->symbol (fn [absolute-dir absolute-path]
                     (-> (if (nil? absolute-dir)
                           absolute-path
                           (str/replace absolute-path (str absolute-dir "/") ""))
                         (str/replace #"\/" ".")
                         (str/replace #"\.clj" "")
                         (str/replace #"\.cljc" "")
                         (str/replace #"\.md" "")
                         clojure.main/demunge
                         symbol))]
      (if (and (instance? java.net.URL path)
               (= (.getProtocol path) "jar"))
        (->symbol nil (subs (last (str/split (.getPath path) #"!")) 1))
        (some (fn [dir]
                (let [absolute-path (.getAbsolutePath (io/file path))
                      absolute-dir (.getAbsolutePath dir)]
                  (when (and (str/starts-with? absolute-path absolute-dir)
                             (fs/exists? path))
                    (->symbol absolute-dir absolute-path))))
              (cp/classpath-directories))))))

(defn- require-find-ns
  [ns*]
  (or (and (try (require ns*) "" (catch Exception _))
           (find-ns ns*))
      (ns->file ns*)
      (ns->jar ns*)))

(defn xref-info-from-path
  [path]
  (let [{:keys [xref ns*]
         clerk-name :clerk/name}
        (if (keyword? path)
          (merge
           {:xref (some-> (get-in @*state [:path-info path]) str)
            :ns* (require-find-ns (get-in @*state [:path-info path]))}
           (meta (get-in @*state [:path-info path])))
          {:xref (str path)
           :ns* (require-find-ns path)})

        notebook-name (or clerk-name
                          (if ns*
                            (or (:clerk/name (meta ns*))
                                xref)
                            (str path)))
        metadata {:notebook-name notebook-name
                  :xref (some-> xref symbol)
                  :clerk-name clerk-name}]
    (if var-changes/*build*
      (let [file-path (if (string? ns*)
                        ns*
                        (clerk.analyzer/ns->file xref))
            expanded-paths (->> (nextjournal.clerk.builder/process-build-opts
                                 (assoc var-changes/*build*
                                        :expand-paths? true))
                                :expanded-paths
                                set)
            ;; `ns->file` may return the absolute file, so we use the
            ;; expanded paths from Clerk instead so the notebooks internal links
            ;; work.
            path-matched (->> expanded-paths
                              (filter #(and file-path (str/ends-with? file-path %)))
                              first)]
        ;; Static build.
        (when-not path-matched
          (println "\n\nWARNING: XRef not found for path " {:path path} "\n\n"))
        {:notebook-name notebook-name
         :path (if (:bundle? var-changes/*build*)
                 (str "#/" path-matched)
                 (some-> path-matched
                         clerk.viewer/doc-url
                         (str/replace #"\.cljc|\.clj|\.md$" ".html")))
         :error (nil? path-matched)
         :metadata metadata})
      {:notebook-name notebook-name
       :path (str "/_ns/" xref)
       :error (nil? ns*)
       :metadata metadata})))

(defmethod prose->output [:md :xref]
  [opts & content]
  (let [{:keys [notebook-name path error metadata]}
        (xref-info-from-path (read-string (first content)))]
    {:type :link
     :content (adapt-content opts [notebook-name])
     :metadata metadata
     :attrs {:href path
             :internal_link "true"
             :class (cond-> link-classes
                      error (conj :bg-red-300))}}))

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

(defmethod prose->output [:md :list]
  [opts & content]
  {:type :bullet-list
   :content (->> content
                 (partition-by #{::hard-break})
                 (remove #{'(::hard-break)})
                 (mapv (fn [v]
                         {:type :list-item
                          :content (adapt-content opts v)}))
                 (adapt-content opts))
   :attrs {:bullet_list "true"}})

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

(defmethod prose->output [:md :note]
  [opts & content]
  {:type :aside
   :content (-> (adapt-content opts content)
                remove-hard-breaks)
   :no-paragraph true})

;; == Main
(defn prose-parser
  "Called when a keyword tag is found.

  E.g. `???:my-keyword{my text}` would give us a `:tag-name` of `:my-keyword` and
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
  (binding [*ns* (or *ns* (find-ns 'clojure.core))]
    (->> (eval-common/eval-forms match)
         (adapt-content {}))))

(defn- auto-resolves [ns]
  (when ns
    (as-> (ns-aliases ns) $
      (assoc $ :current (ns-name *ns*))
      (zipmap (keys $)
              (map ns-name (vals $))))))

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
           (nil? (when *ns*
                   (requiring-resolve (if (qualified-symbol? node-value)
                                        node-value
                                        (symbol (str *ns*) (name node-value)))))))
      [`prose-parser {:tag-name (keyword node-value)}]

      :else
      [node-value])))

;; Alter main parser so we can accept Prose markup.
(alter-var-root #'clerk.parser/parse-markdown
                (constantly
                 (fn parse-markdown-fn
                   [ctx markdown-text]
                   (-> ctx
                       (assoc-in [:content]
                                 [{:type :prose-unevaluated
                                   ;; Trim first character of each new line as they are just
                                   ;; noise generated by the usage `;; ...`.
                                   :content (-> (->> (str/split-lines markdown-text)
                                                     (mapv #(if (= (first %) \space)
                                                              (subs % 1)
                                                              %))
                                                     (str/join "\n"))
                                                ;; Hard breaks are used to build
                                                ;; paragraphs later.
                                                (str/replace #"\n\n" "???:hard-break{}"))}])))))

(defn blocks->markdown [{:as doc :keys [ns]}]
  (swap! *state assoc-in
         [:tags (path->ns (:file doc))]
         [])
  (let [updated-doc
        (-> doc
            (update :blocks
                    #(mapv (fn [block]
                             ;; Evaluate Prose strings, adding a `:toc` field so
                             ;; it can be used to build the table of contents.
                             (if (and (= (-> block :type) :markdown)
                                      (= (-> block :doc :type) :doc))
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
                                       (let [parsed (reader/parse (-> block :doc :content
                                                                      first :content))
                                             result (->> (reader/clojurize parsed)
                                                         eval-clojurized
                                                         remove-hard-breaks)]
                                         result))]
                                 (swap! *state update-in
                                        [:tags (path->ns (:file doc))]
                                        concat
                                        @*collector)
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

(defn process-blocks [viewers doc]
  (let [updated-doc (blocks->markdown doc)]
    (-> updated-doc
        (assoc :atom-var-name->state (clerk.viewer/atom-var-name->state doc))
        (update :blocks (partial into []
                                 (comp (mapcat (partial clerk.viewer/with-block-viewer doc))
                                       (map (comp clerk.viewer/process-wrapped-value
                                                  clerk.viewer/apply-viewers*
                                                  (partial clerk.viewer/ensure-wrapped-with-viewers viewers))))))
        (select-keys [:atom-var-name->state :auto-expand-results? :blocks :bundle?
                      :css-class :open-graph :title :toc :toc-visibility :scope]))))

(def notebook-viewer
  {:name :clerk/notebook
   :transform-fn (fn [{:as wrapped-value :nextjournal/keys [viewers]}]
                   (-> wrapped-value
                       (update :nextjournal/value (partial process-blocks viewers))
                       clerk.viewer/mark-presented))
   :render-fn
   (clerk.viewer/resolve-aliases
    {'render 'nextjournal.clerk.render}
    '(fn render-notebook [{:as _doc xs :blocks :keys [bundle? css-class toc toc-visibility]}]
       (reagent/with-let [local-storage-key "clerk-navbar"
                          !state (reagent/atom {:toc (render/toc-items (:children toc))
                                                :md-toc toc
                                                #_ #_:dark-mode? (localstorage/get-item local-storage-dark-mode-key)
                                                :theme {:slide-over "bg-slate-100 dark:bg-gray-800 font-sans border-r dark:border-slate-900"}
                                                :width 220
                                                :mobile-width 300
                                                :local-storage-key local-storage-key
                                                :set-hash? (not bundle?)
                                                #_ #_:open? (if-some [stored-open?
                                                                 (render/localstorage-get local-storage-key)]
                                                         stored-open?
                                                         (not= :collapsed toc-visibility))})
                          root-ref-fn #(when % (render/setup-dark-mode! !state))
                          ref-fn #(when % (swap! !state assoc :scroll-el %))]
         (let [{:keys [md-toc]} @!state]
           (when-not (= md-toc toc)
             (swap! !state assoc :toc (render/toc-items (:children toc)) :md-toc toc :open? (not= :collapsed toc-visibility)))
           [:<>
            [:div.flex {:ref root-ref-fn}
             [:div.fixed.top-2.left-2.md:left-auto.md:right-2.z-10
              [render/dark-mode-toggle !state]]
             #_(when (and toc toc-visibility)
                 [:<>
                  [navbar/toggle-button !state
                   [:<>
                    [icon/menu {:size 20}]
                    [:span.uppercase.tracking-wider.ml-1.font-bold
                     {:class "text-[12px]"} "ToC"]]
                   {:class "z-10 fixed right-2 top-2 md:right-auto md:left-3 md:top-3 text-slate-400 font-sans text-xs hover:underline cursor-pointer flex items-center bg-white dark:bg-gray-900 py-1 px-3 md:p-0 rounded-full md:rounded-none border md:border-0 border-slate-200 dark:border-gray-500 shadow md:shadow-none dark:text-slate-400 dark:hover:text-white"}]
                  [navbar/panel !state [navbar/navbar !state]]])

             [:div.flex-auto.h-screen.overflow-y-auto.scroll-container.pl-72.relative
              {:ref ref-fn}
              [:div {:class (or css-class "flex flex-col items-center viewer-notebook flex-auto")}
               (doall
                (map-indexed (fn [idx x]
                               (let [{viewer-name :name} (v/->viewer x)
                                     ;; Somehow, `v/css-class` does not exist
                                     ;; for SCI.
                                     viewer-css-class #_(v/css-class x) nil
                                     inner-viewer-name (some-> x v/->value v/->viewer :name)]
                                 ^{:key (str idx "-" @!eval-counter)}
                                 [:div {:class (concat
                                                [(when (:nextjournal/open-graph-image-capture (v/->value x))
                                                   "open-graph-image-capture")]
                                                (if viewer-css-class
                                                  (cond-> viewer-css-class
                                                    (string? viewer-css-class) vector)
                                                  ["viewer"
                                                   (when viewer-name (str "viewer-" (name viewer-name)))
                                                   (when inner-viewer-name (str "viewer-" (name inner-viewer-name)))
                                                   (case (or (v/width x)
                                                             (case viewer-name
                                                               (:code :code-folded) :wide
                                                               :prose))
                                                     ;; `ml-24` is used for the new style.
                                                     :wide "w-full max-w-wide ml-24"
                                                     :full "w-full"
                                                     "w-full max-w-prose px-8")]))}
                                  [v/inspect-presented x]]))
                             xs))]]]]))))})

(defn update-child-viewers [f]
  (fn [viewer]
    (update viewer :transform-fn (fn [transform-fn]
                                   (fn [wrapped-value]
                                     (-> wrapped-value
                                         transform-fn
                                         (update :nextjournal/viewers f)))))))

(def md-viewers
  [{:name :nextjournal.markdown/aside
    :transform-fn (clerk.viewer/into-markup [:aside])}
   {:name :nextjournal.markdown/title-block
    :transform-fn (clerk.viewer/into-markup [:title-block])}
   {:name :nextjournal.markdown/topic
    :transform-fn (clerk.viewer/into-markup [:topic])}
   {:name :nextjournal.markdown/short-rule
    :transform-fn (clerk.viewer/into-markup [:short-rule])}
   {:name :nextjournal.markdown/bullet-list
    :transform-fn (clerk.viewer/into-markup #(vector :ul (:attrs %)))}])

(def ^:private updated-viewers
  (clerk.viewer/update-viewers
   (clerk.viewer/get-default-viewers)
   {(comp #{:clerk/notebook} :name)
    (constantly notebook-viewer)

    (comp #{:markdown} :name)
    (update-child-viewers #(clerk.viewer/add-viewers % md-viewers))}))

(clerk.viewer/reset-viewers! :default updated-viewers)

(defn- app
  [{:as req :keys [uri]}]
  (if (:websocket? req)
    (httpkit/as-channel req clerk.webserver/ws-handlers)
    (try
      (case (get (re-matches #"/([^/]*).*" uri) 1)
        "_ping" {:status 200 :body "ok"}
        "_blob" (clerk.webserver/serve-blob @clerk.webserver/!doc (clerk.webserver/extract-blob-opts req))
        ("build" "js") (clerk.webserver/serve-file "public" req)
        "_ws" {:status 200 :body "upgrading..."}

        (or (when-let [ns-maybe (some-> (get (re-matches #"/_ns/([^/]*).*" uri) 1)
                                        symbol)]
              (when-let [ns* (require-find-ns ns-maybe)]
                ;; We added this so we can recognize a namespace in the uri
                ;; and go there.
                (when (or (not= (str "/_ns/" (:ns @clerk.webserver/!doc))
                                uri)
                          (and (string? ns*)
                               (str/ends-with? ns* (:file @clerk.webserver/!doc))))
                  (clerk/show! ns*))
                {:status  200
                 :headers {"Content-Type" "text/html"
                           "Cache-Control" "no-cache, no-store, must-revalidate"
                           "Pragma" "no-cache"
                           "Expires" "0"}
                 :body    (clerk.view/doc->html {:doc @clerk.webserver/!doc
                                                 :error @clerk.webserver/!error})}))
            (if-let [ns* (:ns @clerk.webserver/!doc)]
              {:status 302
               :headers {"Location" (str "/_ns/" ns*)}}
              {:status 200
               :body   (clerk.view/doc->html {:doc (clerk.webserver/help-doc)
                                              :error @clerk.webserver/!error})})))
      (catch Throwable e
        {:status  500
         :body    (with-out-str (pp/pprint (Throwable->map e)))}))))
(alter-var-root #'clerk.webserver/app (constantly app))

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
