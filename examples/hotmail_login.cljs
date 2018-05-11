(ns examples.hotmail-login
  "An example of how to use the poe/run to log in to hotmail
  NOTE: You have to enter a valid email/password!"
  (:require [poe.core :as poe]))

(defn -main
  [& _]
  (let [url               "https://outlook.live.com/owa/"
        email             "fakey-mc-fakerson@hotmail.com"
        password          "wrong-password"
        signin-selector   ".office-signIn"
        email-selector    "input[type='email']"
        password-selector "//input[@name='passwd'and @type='password']"]
    (poe/run
      {:url   url
       :quit? true}
      [[:click signin-selector]
       [:wait-until-visible email-selector]
       [:input-text email-selector email {:enter? true}]
       [:wait-for-stale password-selector {:by :xpath}]
       [:wait-until-visible password-selector {:by :xpath}]
       [:input-text password-selector password {:enter? true :by :xpath}]])))
