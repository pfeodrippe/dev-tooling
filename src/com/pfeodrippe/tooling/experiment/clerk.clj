^{:nextjournal.clerk/visibility {:code :hide}}
(ns com.pfeodrippe.tooling.experiment.clerk
  #_{:nextjournal.clerk/no-cache true}
  (:require
   [nextjournal.clerk.viewer :as v]
   [nextjournal.clerk :as clerk]
   [com.pfeodrippe.tooling.clerk.parser :as tool.parser :refer [prose->output adapt-content]]
   [nextjournal.clerk.render :as-alias render]))

{::clerk/visibility {:code :hide :result :hide}}

(def portal-url
  "https://cljdoc.org/d/djblue/portal")

(defmethod prose->output [:md :page-name]
  [opts & content]
  {:type :text
   :text ""}
  {:type :title-block
   :content [{:type :topic
              :text content
              :no-paragraph true}
             {:type :short-rule
              :text "Olha asd asdf sdf jsdf oijsd foiajsd foiajsd foiasjd foiajsd ofiajs dfoiajsd foia"
              :no-paragraph true}]
   :no-paragraph true}
  #_{:type :plain
     :content [{:type :heading
                :content (adapt-content opts content)
                :heading-level 1}]
     :toc [1 (adapt-content opts content)]})

(def viewer
  (merge
   tool.parser/notebook-viewer
   {:render-fn
    '(fn render-notebook [{:as _doc xs :blocks :keys [bundle? css-class toc toc-visibility]}]
       (reagent/with-let [local-storage-key "clerk-navbar"
                          !state (reagent/atom {:toc (render/toc-items (:children toc))
                                                :md-toc toc
                                                :dark-mode? (render/localstorage-get
                                                             render/local-storage-dark-mode-key)
                                                :theme {:slide-over "bg-slate-100 dark:bg-gray-800 font-sans border-r dark:border-slate-900"}
                                                :width 220
                                                :mobile-width 300
                                                :local-storage-key local-storage-key
                                                :set-hash? (not bundle?)
                                                :open? (if-some [stored-open?
                                                                 (render/localstorage-get local-storage-key)]
                                                         stored-open?
                                                         (not= :collapsed toc-visibility))})
                          root-ref-fn #(when % (render/setup-dark-mode! !state))
                          ref-fn #(when % (swap! !state assoc :scroll-el %))]
         (let [{:keys [md-toc]} @!state]
           (when-not (= md-toc toc)
             (swap! !state assoc :toc (render/toc-items (:children toc)) :md-toc toc :open? (not= :collapsed toc-visibility)))
           [:<>
            [:<>
             [:style {:type "text/css"}
              "
aside {
    margin-bottom: 2em;
    width: 12rem;
    padding-left: .5rem;
    margin-left: -14rem;
    float: left;
    text-align: right;
    position: absolute;
}

aside > p {
    color: #667;
    font-size: 1rem;
    font-family: PT Serif;
}

p {
    font-family: 'Fira Sans', sans-serif;
    color: black;
    font-size: 1.1rem;
}

title-block {

    position: absolute;
    margin-bottom: 2em;
    border-top: solid 3px #333;
    padding-top: 5px;
    width: 8rem;
    padding-left: .5rem;
    margin-left: -180px;
    float: left;
    text-align: right;

    font-family: 'Fira Sans', sans-serif;
    color: black;
    font-size: 1.1rem;
}

title-block topic {
    display:block;
    font-family: inherit;
    text-transform: inherit;
    letter-spacing: inherit;
    font-size: 125%;
    line-height: 1.10;
    border-bottom: inherit;
    margin-top: 0.8rem;
    margin-bottom: 0.8rem;
    font-weight: bolder;
    border-top: 0;
    padding-top: 0;
}

short-rule {
    display: block;
    font-size: 100%;
    line-height: 1.25;
    font-style: italic;
    hyphens: none;
}
"]]
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
                                                     :wide "w-full max-w-wide"
                                                     :full "w-full"
                                                     "w-full max-w-prose px-8")]))}
                                  [v/inspect-presented x]]))
                             xs))]]]])))}))

(clerk/add-viewers! [viewer])

{::clerk/visibility {:code :fold :result :show}}

;; ◊page-name{portal}

;; Let's learn what you can do with Portal.

;; ◊title{What's Portal?}

#_^{:nextjournal.clerk/visibility {:code :show}}
{:a 10 :f "asdjasdijfsodifjsodifjo" :c "dsfjasdfoijasdi" :d "fadsfjasiofjoiasdjfa" :e "fasdasdasd" :ff "asdasdasdsa"}

;; Take a look at ◊link[portal-url] for more
;; information, Portal has excellent guides.

;; ◊title{Malli Schemas}

;; ◊note{This is just some note, don't bother ◊link{https://google.com}{This is google}}

;; For any Malli schema that you find in Portal, you can generate some
;; samples for it, for this you use the exercise schema command, click
;; in one of the schemas in the Portal window below, open the commands panel
;; by pressing ◊command{CMD + SHIFT + p} or by clicking at ◊em{>_} in the bottom
;; right and type ◊em{exercise}.

;; ◊note{This is just some note, don't bother ◊link{https://google.com}{This is google}}

;; You will see a map with ◊code{:malli/generated} as the key and the
;; various samples on the right. If you double click (or press
;; ◊command{ENTER}) on ◊code{:malli/generated}, you will see that you are able to
;; generate even more samples, this is a easy way to visualize ◊strong{any}
;; schema you meet in Portal, from any source.

;; So what?

;; asdf asdfasdf

;; asdf jasdiof jaoisdf jaoids jfaoisd jaosf jaoisdf jaos ijaoi djaoi sdjfas
;; asd fjaoisd jaosd

{::clerk/visibility {:code :hide :result :hide}}

(comment

  (clerk/serve! {:watch-paths ["src/com/pfeodrippe/tooling/experiment/"]})

  (clerk/build! {:paths ["src/com/pfeodrippe/tooling/experiment/**"]
                 :bundle true
                 :browse true})

  ;; TODO:
  ;; - [x] Divide text in multiple columns for asides
  ;; - [ ] Page title
  ;; - [ ] Subtitle
  ;; - [ ] Improve external link UI
  ;; - [ ] xref
  ;; - [ ] Search
  ;; - [ ] Index
  ;; - [ ] Glossary

  ())
