(ns examples.google-search
  "An example of how to use the poe/run to perform a google search"
  (:require [poe.core :as poe]
            [poe.webdriver.webdriver :refer [until]]))

(defn -main
  [& _]
  (let [url                   "http://www.google.com"
        search-input-selector "q"
        search-input          "altered carbon poe"]
    (poe/run
      {:url url}
      [[:input-text search-input-selector search-input {:by :name :enter? true}]
       #(.wait poe/driver (.titleIs until (str search-input " - Google Search")) 1000)
       [:click "Images" {:by :linkText}]])))
