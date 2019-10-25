(ns examples.table
  "An example of how to use poe/run to extract simple table data from a page

  lumo --classpath src:examples -m examples.table
  "
  (:require [poe.core :as poe]
            [poe.html.scrape :as html-scrape]
            [poe.file-export :as file-export]))

(def cwd (js/process.cwd))

(defn run-extraction
  ([url {:keys [filename directory quit?]
         :or {directory cwd
              filename  "extract-table.csv"
              quit?     true}
         :as opts}]
   (poe.core/run {:url   url
                  :quit? quit?}
     [#(html-scrape/scrape-table poe/driver opts)
      (fn [rows]
        (let [file (str directory "/" filename)]
          (js/console.log (str "Exporting table data to: " file))
          (file-export/csv file rows)))])))

(defn run-demo
  "Visit https://nvd.nist.gov/vuln/categories, and scrape an html table from the page, then save results as csv"
  ([]
   (run-demo {}))
  ([opts]
   ;; https://www.guru99.com/xpath-selenium.html for more xpath fun!
   (run-extraction "https://nvd.nist.gov/vuln/categories"
                   (merge {:selector        "//table[@data-testid='vuln-feed-table']"
                           :by              :xpath
                           :include-header? true
                           :quit?           true}
                          opts))))

(defn -main
  "Run a demo, illustrating how to scrape data and store as csv"
  [& _]
  (run-demo))

(comment
  ;; `lumo --classpath src:examples/` to start a repl, then run the following
  ;; This will keep the webdriver session open so you are free to keep tinkering
  (require '[examples.table :as t])
  (t/run-demo {:include-header? true
               :quit?           false})
  )
