(ns com.pfeodrippe.tooling.clerk.var-changes
  (:require
   [hiccup.page :as hiccup]
   [nextjournal.clerk.viewer :as v]
   [nextjournal.clerk.view :as view :refer [include-css+js]]
   [nextjournal.clerk.builder :as clerk.builder :refer [process-build-opts compile-css! write-static-app! doc-url]]
   [nextjournal.clerk.eval :as eval]
   [nextjournal.clerk.parser :as parser]
   [nextjournal.clerk.analyzer :as analyzer]))

(def ^:private style
  "
@font-face {
font-family: concourse_index;
font-style: normal;
font-weight: normal;
font-stretch: normal;
font-display: auto;
src: url('../build/concourse_index_regular.woff2') format('woff2');
}

ol li:before {
    counter-increment: foobar;
    content: counter(foobar);
    font-family: \"concourse_index\";
    font-size: 1.5rem;
    position: absolute;
    margin-left: -3.0rem;
    margin-top: -4px;
}

ul[bullet_list] li:before {
    content: \"•\";
    font-family: \"concourse_index\";
    position: absolute;
    font-size: 1.2rem;
    margin-top: 0;
    margin-left: -3rem;
}

ol, ul {
    counter-reset: foobar;
}

ol li, ul li {
    margin-left: 0.8rem;
    list-style: none;
}

li {
    margin-bottom: 1em !important;
}

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
    font-family: 'Roboto Slab', serif;
}

p {
    font-family: 'Roboto Slab', serif;
    color: black;
    font-size: 1.1rem;
}

title-block {

    position: absolute;
    margin-bottom: 2em;
    border-top: solid 3px #333;
    padding-top: 5px;
    width: 10rem;
    padding-left: .5rem;
    margin-left: -12rem;
    float: left;
    text-align: right;

    font-family: 'Roboto Slab', serif;
    color: black;
    font-size: 1.1rem;
}

title-block topic {
    display:block;
    font-family: 'Roboto Slab', serif;
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

a[external_link] {
    color: inherit !important;
}

a[external_link]::after {
    position: relative;
    content: \"﻿°\";
    margin-left: 0em;
    font-size: 90%;
    top: -0.1em;
    color: rgb(153, 51, 51);
    font-feature-settings: \"caps\";
    font-variant-numeric: normal;
}

a[internal_link] {
    color: inherit !important;
    // font-variant-caps: all-small-caps;
    // font-family: PT Serif;
    letter-spacing: 2px;
    text-transform: uppercase;
    font-size: 17px;
}

")

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn ->html [{:as state :keys [conn-ws?] :or {conn-ws? true}}]
  (hiccup/html5
   [:head
    [:meta {:charset "UTF-8"}]
    [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]

    ;; For preventing favicon requests.
    [:link {:rel :icon :href "data:,"}]

    ;; Add some fonts.
    [:link {:href "https://fonts.googleapis.com/css?family=Roboto+Slab:400,100,300,700&subset=latin,latin-ext"
            :rel "stylesheet"
            :type "text/css"}]

    [:style {:type "text/css"} style]

    (include-css+js state)]
   [:body.dark:bg-gray-900
    [:div#clerk]
    [:script {:type "module"} "let viewer = nextjournal.clerk.sci_env
let state = " (-> state v/->edn pr-str) "
viewer.set_state(viewer.read_string(state))
viewer.mount(document.getElementById('clerk'))\n"
     (when conn-ws?
       "const ws = new WebSocket(document.location.origin.replace(/^http/, 'ws') + '/_ws')
ws.onmessage = viewer.onmessage;
window.ws_send = msg => ws.send(msg)")]]))
(alter-var-root #'view/->html (constantly ->html))

(defn ->static-app [{:as state :keys [current-path html]}]
  (hiccup/html5
   [:head
    [:title (or (and current-path (-> state :path->doc (get current-path) v/->value :title)) "Clerk")]
    [:meta {:charset "UTF-8"}]
    [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]

    ;; For preventing favicon requests.
    [:link {:rel :icon :href "data:,"}]

    ;; Add some fonts.
    [:link {:href "https://fonts.googleapis.com/css?family=Roboto+Slab:400,100,300,700&subset=latin,latin-ext"
            :rel "stylesheet"
            :type "text/css"}]

    [:style {:type "text/css"} style]

    (when current-path (v/open-graph-metas (-> state :path->doc (get current-path) v/->value :open-graph)))
    (include-css+js state)]
   [:body
    [:div#clerk-static-app html]
    [:script {:type "module"} "let viewer = nextjournal.clerk.sci_env
let app = nextjournal.clerk.static_app
let opts = viewer.read_string(" (-> state v/->edn pr-str) ")
app.init(opts)\n"]]))
(alter-var-root #'view/->static-app (constantly ->static-app))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn build-static-app! [{:as opts :keys [bundle?]}]
  (let [{:as opts :keys [download-cache-fn upload-cache-fn report-fn compile-css? expanded-paths error]}
        (try (process-build-opts (assoc opts :expand-paths? true))
             (catch Exception e
               {:error e}))
        start (System/nanoTime)
        state (mapv #(hash-map :file %) expanded-paths)
        _ (report-fn {:stage :init :state state :build-opts opts})
        _ (when error
            (report-fn {:stage :parsed :error error :build-opts opts})
            (throw error))
        {state :result duration :time-ms} (eval/time-ms (mapv (comp (partial parser/parse-file {:doc? true}) :file) state))
        _ (report-fn {:stage :parsed :state state :duration duration})
        {state :result duration :time-ms} (eval/time-ms (reduce (fn [state doc]
                                                                  (try (conj state (-> doc analyzer/build-graph analyzer/hash))
                                                                       (catch Exception e
                                                                         (reduced {:error e}))))
                                                                []
                                                                state))
        _ (if-let [error (:error state)]
            (do (report-fn {:stage :analyzed :error error :duration duration})
                (throw error))
            (report-fn {:stage :analyzed :state state :duration duration}))
        _ (when download-cache-fn
            (report-fn {:stage :downloading-cache})
            (let [{duration :time-ms} (eval/time-ms (download-cache-fn state))]
              (report-fn {:stage :done :duration duration})))
        state (mapv (fn [{:as doc :keys [file]} idx]
                      (report-fn {:stage :building :doc doc :idx idx})
                      (let [{result :result duration :time-ms} (eval/time-ms
                                                                (try
                                                                  (let [doc (binding [v/doc-url (partial doc-url opts state file)]
                                                                              (eval/eval-analyzed-doc doc))]
                                                                    (assoc doc :viewer (view/doc->viewer (assoc opts :inline-results? true) doc)))
                                                                  (catch Exception e
                                                                    {:error e})))]
                        (report-fn (merge {:stage :built :duration duration :idx idx}
                                          (if (:error result) result {:doc result})))
                        result)) state (range))
        _ (when-let [first-error (some :error state)]
            (throw first-error))
        opts (if compile-css?
               (do
                 (report-fn {:stage :compiling-css})
                 (let [{duration :time-ms opts :result} (eval/time-ms (compile-css! opts state))]
                   (report-fn {:stage :done :duration duration})
                   opts))
               opts)
        {state :result duration :time-ms} (eval/time-ms (write-static-app! opts state))]
    (when upload-cache-fn
      (report-fn {:stage :uploading-cache})
      (let [{duration :time-ms} (eval/time-ms (upload-cache-fn state))]
        (report-fn {:stage :done :duration duration})))
    (report-fn {:stage :finished :state state :duration duration :total-duration (eval/elapsed-ms start)})))
(defonce ^:dynamic *build* nil)
(alter-var-root #'clerk.builder/build-static-app!
                (constantly (fn [opts]
                              (binding [*build* opts]
                                (build-static-app! opts)))))
