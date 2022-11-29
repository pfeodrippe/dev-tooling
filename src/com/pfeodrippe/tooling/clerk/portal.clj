(ns com.pfeodrippe.tooling.clerk.portal
  (:require
   [clojure.string :as str]
   [nextjournal.clerk :as clerk]
   [com.pfeodrippe.tooling.portal :as tool.portal]
   [portal.api :as portal]))

(defn- submit
  [value]
  (portal/submit value))

(defonce *portal (atom nil))

(defn setup-portal
  ([launcher]
   (setup-portal launcher {}))
  ([launcher opts]
   (or @*portal
       (do
         (remove-tap #'submit)
         (add-tap #'submit)
         (reset! *portal
                 (portal/open (merge {:launcher launcher
                                      :theme :portal.colors/nord-light}
                                     opts)))))))

(def portal-viewer
  {:name :portal
   :transform-fn
   (fn [value]
     (let [portal-url (cond
                        (instance? portal.runtime.jvm.client.Portal (:nextjournal/value value))
                        (portal/url (:nextjournal/value value))

                        (seq (:nextjournal/value value))
                        (portal/url
                         (portal/open {:launcher false
                                       :value (:nextjournal/value value)
                                       :theme :portal.colors/nord-light}))

                        :else
                        (portal/url (setup-portal false)))]
       {:nextjournal/value
        (if-let [portal-host (System/getenv "PORTAL_HOST")]
          (str "http://" portal-host "?"
               (-> portal-url (str/split #"\?") last))
          portal-url)

        :nextjournal/width :full}))
   :render-fn '#(v/html [:iframe
                         {:src %
                          :style {:width "90%"
                                  :height "60vh"
                                  :margin-left :auto
                                  :margin-right :auto
                                  :border-left "1px solid #d8dee9"
                                  :border-right "1px solid #d8dee9"
                                  :border-bottom "1px solid #d8dee9"
                                  :border-radius "2px"}}])})

(comment

  ())
