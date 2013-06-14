(ns karmag.ants.util.optimizer
  (:use karmag.ants.protocol)
  (:require (karmag.ants.util (time :as time-util-)))
  (:import java.io.Writer))

;;--------------------------------------------------
;; setup

(defn- make-writer-fn [writer]
  (if writer
    (fn [fmt & args]
      (.write ^Writer writer ^String (apply format fmt args))
      (.write ^Writer writer (str \newline))
      (.flush ^Writer writer))
    (constantly nil)))

(defn- get-interval [interval]
  (or (time-util-/to-millis interval)
      10000))

(defn- make-get-value-fn [f]
  (or f (fn [prc]
          (:completed-task-count (status prc)))))

(defn- make-inc-fn [f]
  (or f (fn [prc]
          (let [curr (:maximum-pool-size (status prc))]
            (configure prc :maximum-pool-size (inc curr)))
          (str (:maximum-pool-size (status prc)) " threads"))))

(defn- make-dec-fn [f]
  (or f (fn [prc]
          (let [curr (:maximum-pool-size (status prc))
                lower (dec curr)]
            (when (pos? lower)
              (configure prc :maximum-pool-size lower))
            (str (:maximum-pool-size (status prc)) " threads")))))

;;--------------------------------------------------
;; run

(defn- get-data [processor config]
  {:value ((:get-value-fn config) processor)
   :time (System/currentTimeMillis)})

(defn- get-speed [first-data second-data]
  (/ (- (:value second-data) (:value first-data))
     (- (:time second-data) (:time first-data))
     1/1000))

(defn- make-thread [processor running config]
  (Thread.
   (fn []
     (let [{:keys [write-fn interval inc-fn dec-fn]} config
           state (atom (assoc (get-data processor config)
                         :speed 0
                         :modifier-fn inc-fn))]
       (try
         (while @running
           (Thread/sleep interval)
           (let [data (get-data processor config)
                 speed (get-speed @state data)
                 faster (< (:speed @state) speed)
                 modifier-fn (if faster
                               (:modifier-fn @state)
                               (-> #{inc-fn dec-fn}
                                   (disj (:modifier-fn @state))
                                   first))
                 mod-value (modifier-fn processor)]
             (write-fn "(%s)  Spd: %.5f -> %.5f  Action: %s  Report: %s"
                       (str (java.util.Date.))
                       (float (:speed @state))
                       (float speed)
                       (if (= modifier-fn inc-fn) "inc" "dec")
                       (str mod-value))
             (swap! state merge
                    data
                    {:speed speed, :modifier-fn modifier-fn})))
         (catch Throwable t
           (write-fn (str "Exception caught: " t))))))))

;;--------------------------------------------------
;; interface

(defn start [processor opts]
  (let [{:keys [writer interval get-value inc dec]} opts
        config {:write-fn (make-writer-fn writer)
                :interval (get-interval interval)
                :get-value-fn (make-get-value-fn get-value)
                :inc-fn (make-inc-fn inc)
                :dec-fn (make-dec-fn dec)}
        running (atom true)
        thread (make-thread processor running config)]
    (.start ^Thread thread)
    {:running running, :thread thread}))

(defn release [optimizer]
  (let [{:keys [running thread]} optimizer]
    (reset! running false)
    (.interrupt ^Thread thread)))
