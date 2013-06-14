(ns karmag.ants.util.time
  (:use karmag.ants.protocol)
  (:import java.util.concurrent.TimeUnit))

(defn to-millis [time]
  (cond
   (vector? time) (let [[amount tu] time]
                    (.toMillis ^TimeUnit (to-timeunit tu) amount))
   (integer? integer?) time
   :else nil))
