(ns karmag.ants.core
  (:require (karmag.ants (protocol :as protocol-))
            (karmag.ants.processor
             (variable-pool-processor :as processor-))
            (karmag.ants.util (optimizer :as optimizer-)
                              (time :as time-util-))))

(def add       protocol-/add)
(def add-all   protocol-/add-all)
(def shutdown  protocol-/shutdown)
(def abort     protocol-/abort)
(def wait-for  protocol-/wait-for)
(def status    protocol-/status)
(def configure protocol-/configure)

(defn wait-while [processor & {:as opts}]
  (let [{:keys [pred interval]} opts
        pred (or pred processor-/working?)
        interval (or (time-util-/to-millis interval) 1000)]
    (while (pred processor)
      (Thread/sleep interval))))

(defn done? [processor]
  (not (processor-/working? processor)))

(defn make-processor []
  (processor-/make))

(defn start-optimizer [processor & {:as opts}]
  (optimizer-/start processor opts))

(defn release-optimizer [optimizer]
  (optimizer-/release optimizer))
