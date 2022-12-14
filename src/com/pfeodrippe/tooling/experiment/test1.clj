^{:nextjournal.clerk/visibility {:code :hide}}
(ns com.pfeodrippe.tooling.experiment.test1
  {:clerk/name "test 1"}
  (:require
   [nextjournal.clerk :as clerk]
   [com.pfeodrippe.tooling.clerk.parser :as tool.parser :refer [prose->output adapt-content]]))

{::clerk/visibility {:code :hide :result :hide}}

(def portal-url
  "https://cljdoc.org/d/djblue/portal")

{::clerk/visibility {:code :fold :result :show}}

;; ◊page-name[{:subtitle "Getting started with Portal"}]{test 1}

;; ◊(if com.pfeodrippe.tooling.clerk.var-changes/*build* "Build!" "Oh no")

;; Let's learn what you can do with Portal.

;; ◊title{What's Portal?}

;; ◊xref{com.pfeodrippe.tooling.experiment.test2}

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
  ;; - [x] Page title
  ;; - [x] Subtitle
  ;; - [x] Improve external link UI
  ;; - [ ] Internal link (xref)
  ;; - [ ] Search
  ;; - [ ] Index
  ;; - [ ] Glossary
  ;; - [ ] Fix loading glitch
  ;; - [ ] Make it work with Mobile

  ())
