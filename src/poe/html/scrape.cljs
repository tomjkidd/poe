(ns poe.html.scrape
  (:require [poe.webdriver.webdriver :as webdriver]))

(defn scrape-table
  "Attempts to scrape an html table from the current page.
  Makes no attempts to really handle errors, and assumes a simple html table structure:
  (closing tags omitted from brevity)

  <table>
    <thead>
     <tr>
      <th>...
    <tbody>
     <tr>
      <td>...

  Inspired, at least in spirit, by:
  https://www.techbeamers.com/handling-html-tables-selenium-webdriver/
  "
  [driver {:keys [selector by include-header?]
           :or   {selector        "table"
                  by              :css
                  include-header? true}}]
  (let [header-promise
        (if include-header?
          (-> (.findElement driver
                            (webdriver/by* by selector))
              (.then (fn [table]
                       (.findElement table (webdriver/by* :css "thead"))))
              (.then (fn [thead]
                       (.findElement thead (webdriver/by* :css "tr"))))
              (.then (fn [tr]
                       (.findElements tr (webdriver/by* :css "th"))))
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
        (-> (.findElement driver (webdriver/by* by selector))
            (.then (fn [table]
                     (.findElement table (webdriver/by* :css "tbody"))))
            (.then (fn [tbody]
                     (.then (.findElements tbody
                                           (webdriver/by* :css "tr"))
                            (fn [rows]
                              (let [promise-row-list
                                    (map-indexed
                                     (fn [idx row]
                                       (.then
                                        (.findElements (get (js->clj rows) idx)
                                                       (webdriver/by* :css "td"))
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
             (let [header-row-clj (js->clj header-row)]
               (into (cond-> []
                       (seq header-row-clj) (conj header-row-clj))
                     (js->clj body-rows)))))))
