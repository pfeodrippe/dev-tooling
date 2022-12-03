^{:nextjournal.clerk/visibility {:code :hide}}
(ns com.pfeodrippe.tooling.experiment.clerk
  {:nextjournal.clerk/no-cache true}
  (:require
   [nextjournal.clerk :as clerk]
   com.pfeodrippe.tooling.clerk.parser))

{::clerk/visibility {:code :hide :result :hide}}

(def portal-url
  "https://cljdoc.org/d/djblue/portal")

{::clerk/visibility {:code :fold :result :show}}

;; ◊page-name{Portal 🔮}

;; Let's learn what you can do with Portal.

;; ◊title{What's Portal?}

;; Take a look at ◊link[portal-url] for more
;; information, Portal has excellent guides.

;; ◊title{Malli Schemas 🕶️}

;; For any Malli schema that you find in Portal, you can generate some
;; samples for it, for this you use the exercise schema command, click
;; in one of the schemas in the Portal window below, open the commands panel
;; by pressing ◊command{CMD + SHIFT + p} or by clicking at ◊em{>_} in the bottom
;; right and type ◊em{exercise}.

;; You will see a map with ◊code{:malli/generated} as the key and the
;; various samples on the right. If you double click (or press
;; ◊command{ENTER}) on ◊code{:malli/generated}, you will see that you are able to
;; generate even more samples, this is a easy way to visualize ◊strong{any}
;; schema you meet in Portal, from any source.

{::clerk/visibility {:code :hide :result :hide}}

(comment

  (clerk/serve! {:watch-paths ["src/com/pfeodrippe/tooling/experiment/"]})

  ())
