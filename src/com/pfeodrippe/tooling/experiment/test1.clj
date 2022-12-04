(ns com.pfeodrippe.tooling.experiment.test1
  {:nextjournal.clerk/no-cache true
   :nextjournal.clerk/visibility {:code :hide :result :hide}}
  (:require
   [nextjournal.clerk :as clerk]
   [nextjournal.clerk.viewer :as v]
   [com.pfeodrippe.tooling.clerk.parser :as tool.clerk.parser]))

(def title "My title")
(def topic "My topic")

{:nextjournal.clerk/visibility {:result :show}}

#_(v/html
 [:<>
  [:style {:type "text/css"}
   "
aside {
    width: 40%;
    padding-left: .5rem;
    margin-left: .5rem;
    float: right;
    box-shadow: inset 5px 0 5px -5px #29627e;
    font-style: italic;
    color: #29627e;
}

aside > p {
    margin: .5rem;
}

p {
    font-family: 'Fira Sans', sans-serif;
}
"]])

#_(v/html
   [:<>
    [:style {:type "text/css"}
     "
aside {
    width: 40%;
    padding-left: .5rem;
    margin-left: .5rem;
    float: right;
    box-shadow: inset 5px 0 5px -5px #29627e;
    font-style: italic;
    color: #29627e;
}

aside > p {
    margin: .5rem;
}

p {
    font-family: 'Fira Sans', sans-serif;
}
"]])

#_(clerk/with-viewer v/html
  "<!DOCTYPE html>
<html>
<head>
    <link rel=\"stylesheet\" href=\"https://unpkg.com/tailwindcss@^2/dist/tailwind.min.css\" />
</head>
<body>
    <div class=\"grid grid-cols-2 gap-4\">
        <div class=\"col-span-1 font-italic\">
            Callout 1: asdifj asoidfj asdoif jasdo fasdio fjsaiod fjoisad jfoiasdj fioasdj fiojasdoi fjaosid fjaiosdf j
        </div>
        <p class=\"col-span-1\">
            Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat.
        </p>
        <div class=\"col-span-1 font-italic\">
            Callout 2:
        </div>
        <p class=\"col-span-1\">
            Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat.
        </p>
    </div>
</body>
</html>
")
