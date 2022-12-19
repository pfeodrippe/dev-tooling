^{:nextjournal.clerk/visibility {:code :hide}}
(ns com.pfeodrippe.tooling.experiment.index
  {:nextjournal.clerk/no-cache true}
  (:require
   [nextjournal.clerk :as clerk]
   [com.pfeodrippe.tooling.clerk :as tool.clerk]))

{::clerk/visibility {:code :hide :result :hide}}

(tool.clerk/add-path-info!
 {:doc/test-1 `com.pfeodrippe.tooling.experiment.test1})

{::clerk/visibility {:code :hide :result :show}}

;; ◊page-name[{:subtitle "Index"}]{Mobile Guide}

(tool.clerk/view-index
 [{:title "Introduction"
   :pages [:doc/ggag
           :doc/test-1]}
  {:title "Services"}
  {:title "Tools"}
  {:title "Domain"}])

{::clerk/visibility {:code :hide :result :hide}}

(comment

  (clerk/serve! {:watch-paths ["src/com/pfeodrippe/tooling/experiment/"]})

  (clerk/build! {:paths ["src/com/pfeodrippe/tooling/experiment/**"]
                 :index "src/com/pfeodrippe/tooling/experiment/index.clj"
                 :bundle true
                 :browse true})

  ;; TODO:
  ;; - [x] Divide text in multiple columns for asides
  ;; - [x] Page title
  ;; - [x] Subtitle
  ;; - [x] Improve external link UI
  ;; - [x] Internal link (xref)
  ;;   - [x] Go back to last page
  ;;   - [x] Static build
  ;;   - [x] Uppercase
  ;;   - [x] Can refer to a ns using keyword as well (ns metadata or maybe a EDN
  ;;         file or even another ns that contains the mapping)?
  ;; - [x] Fix absolute path issue
  ;; - [x] Redirect
  ;; - [-] Change ns automatically in the url when the user goes to another ns.
  ;;       Not for now
  ;; - [x] Show red for a xref that doesn't exist
  ;; - [x] Index
  ;;   - [x] Section header
  ;;     - [x] Children
  ;;   - [-] Section page (for the future)
  ;; - [x] Fix loading glitch
  ;; - [x] In the JVM, show background in red for a link if it does not exist
  ;; - [x] Use free concourse font
  ;;   - [x] Ordered lists
  ;;   - [x] Unordered lists
  ;; - [x] Support md files
  ;;   - [x] Check static build
  ;;   - [x] Make xref work pointing to it
  ;; - [ ] Add footnotes
  ;; - [ ] Add inline blocks
  ;; - [ ] Navigation (bottom bar)
  ;;   - [ ] Create navigation based on index
  ;; - [ ] Search
  ;; - [ ] Glossary
  ;; - [ ] Make it work with Mobile
  ;; - [ ] Make scroll remember its position when clicking back or when
  ;;       reloading the same page
  ;; - [ ] Remove `Generated with Clerk` from the static build
  ;; - [ ] Breadcrumb
  ;; - [ ] For xref, refer to an marker in a file
  ;; - [ ] PDF version?
  ;; - [ ] Ebook version?
  ;; - [ ] Support comment
  ;; - [ ] Create Clerk text game
  ;; - [ ] Fix flashing font

  ())
