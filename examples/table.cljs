(ns examples.table
  "An example of how to use poe/run to extract simple table data from a page

  lumo --classpath src:examples -m examples.table
  "
  (:require [poe.core :as poe]
            [cljs.reader :as cljs-reader]))

(def fs (js/require "fs"))
(def csv-stringify (js/require "csv-stringify/lib/sync"))

(defn slurp
  "Read a file, as a string"
  [path]
  (str (.readFileSync fs path)))

(defn slurp-edn
  "Read a file, as edn"
  [path]
  (-> path
      slurp
      cljs-reader/read-string))

(defn spit
  "Write a string to file"
  [f data]
  (.writeFileSync fs f data))

(def cwd (js/process.cwd))

(defn scrape-html-table
  "Attempts to scrape an html table from the current page.
  Makes no attempts to really handle errors, and assumes a simple html table structure:
  (closing tags omitted from brevity)

  <table>
    <thead>
     <tr>
      <th>...
    <tbody>
     <tr>
      <td>..."
  [driver {:keys [selector by include-header?]
           :or   {selector        "table"
                  by              :css
                  include-header? true}}]
  (let [header-promise
        (if include-header?
          (-> (.findElement driver
                            (poe/by* by selector))
              (.then (fn [table]
                       (.findElement table (poe/by* :css "thead"))))
              (.then (fn [thead]
                       (.findElement thead (poe/by* :css "tr"))))
              (.then (fn [tr]
                       (.findElements tr (poe/by* :css "th"))))
              (.then (fn [cols]
                       (let [promise-col-list
                             (map-indexed
                              (fn [col-idx col]
                                (.getText (get (js->clj cols) col-idx)))
                              cols)

                             promise-col-array (clj->js promise-col-list)]
                         (js/Promise.all promise-col-array)))))
          (js/Promise.resolve (clj->js [])))

        body-promise
        (-> (.findElement driver (poe/by* by selector))
            (.then (fn [table]
                     (.findElement table (poe/by* :css "tbody"))))
            (.then (fn [tbody]
                     (.then (.findElements tbody
                                           (poe/by* :css "tr"))
                            (fn [rows]
                              (let [promise-row-list
                                    (map-indexed
                                     (fn [idx row]
                                       (.then
                                        (.findElements (get (js->clj rows) idx)
                                                       (poe/by* :css "td"))
                                        (fn [cols]
                                          (let [promise-col-list
                                                (map-indexed
                                                 (fn [col-idx col]
                                                   (.getText (get (js->clj cols) col-idx)))
                                                 cols)

                                                promise-col-array (clj->js promise-col-list)]
                                            (js/Promise.all promise-col-array)))))
                                     rows)

                                    promise-row-array (clj->js promise-row-list)]
                                (js/Promise.all promise-row-array)))))))]
    (.then (js/Promise.all (clj->js [header-promise body-promise]))
           (fn [[header-row body-rows]]
             (print {:header-row header-row
                     :body-rows body-rows})
             (let [header-row-clj (js->clj header-row)]
               (into (cond-> []
                       (seq header-row-clj) (conj header-row-clj))
                     (js->clj body-rows)))))))

(defn run-extraction
  ([url {:keys [filename directory quit?]
         :or {directory cwd
              filename  "extract-table.csv"
              quit?     true}
         :as opts}]
   (poe.core/run {:url   url
                  :quit? quit?}
     [#(scrape-html-table poe/driver opts)
      (fn [rows]
        (let [file (str directory "/" filename)]
          (js/console.log (str "Exporting table data to: " file))
          (spit file (csv-stringify (clj->js rows)))))])))

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
               :quit?           false}))
