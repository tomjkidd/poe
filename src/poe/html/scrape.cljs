(ns poe.html.scrape
  (:require [poe.webdriver.webdriver :as webdriver]))

(defn scrape-table
  "Attempts to scrape an html table from the current page.
  Makes no attempts to really handle errors, and assumes a simple html table structure:
  (closing tags omitted for brevity)

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
                       (reduce (fn [acc-promise col-promise]
                                 (.then acc-promise
                                        (fn [acc]
                                          (.then (.getText col-promise)
                                                 #(conj acc %)))))
                               (js/Promise.resolve [])
                               cols))))
          (js/Promise.resolve []))

        body-promise
        (-> (.findElement driver (webdriver/by* by selector))
            (.then (fn [table]
                     (.findElement table (webdriver/by* :css "tbody"))))
            (.then (fn [tbody]
                     (.findElements tbody
                                    (webdriver/by* :css "tr"))))
            (.then (fn [trs]
                     (reduce
                      (fn [row-acc-promise tr-promise]
                        (.then row-acc-promise
                               (fn [row-acc]
                                 (.then (.findElements tr-promise (webdriver/by* :css "td"))
                                        (fn [tds]
                                          (.then (reduce (fn [col-acc-promise td-promise]
                                                           (.then col-acc-promise
                                                                  (fn [col-acc]
                                                                    (.then (.getText td-promise)
                                                                           #(conj col-acc %)))))
                                                         (js/Promise.resolve [])
                                                         tds)
                                                 (fn [row]
                                                   (conj row-acc row))))))))
                      (js/Promise.resolve [])
                      trs))))]
    (.then header-promise
           (fn [header-row]
             (.then body-promise
                    (fn [body-rows]
                      (into (cond-> []
                              (seq header-row) (conj header-row))
                            body-rows)))))))
