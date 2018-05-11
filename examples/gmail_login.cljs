(ns examples.gmail-login
  "An example of how to use the poe/run to log in to gmail
  NOTE: You have to enter a valid email/password!"
  (:require [poe.core :as poe]))

(defn -main
  [& _]
  (let [url               "https://mail.google.com"
        email             "fake@gmail.com"
        password          "fake-password"
        email-selector    "input[type='email']"
        password-selector "//input[@name='password' and @type='password']"]
    (poe/run
      {:url   url
       :quit? true}
      [[:wait-until-visible email-selector]
       [:input-text email-selector email {:enter? true}]
       [:wait-until-located password-selector {:by :xpath}]
       [:wait-until-visible password-selector {:by :xpath}]
       [:input-text password-selector password {:enter? true :by :xpath}]])))
