(ns karmag.ants.processor.variable-pool-processor
  (:use karmag.ants.protocol)
  (:require (karmag.ants.util (time :as time-util-)))
  (:import (java.util.concurrent LinkedBlockingQueue
                                 ThreadPoolExecutor)))

(extend-type ThreadPoolExecutor
  Processor
  (add [this task]
    (.submit this ^Callable task))
  (add-all [this task-coll]
    (doall (map #(.submit this ^Callable %) task-coll)))
  (shutdown [this]
    (.shutdown this))
  (abort [this]
    (.shutdownNow this))
  (wait-for [this time timeunit]
    (shutdown this)
    (.awaitTermination this time (to-timeunit timeunit)))
  (status [this]
    {:keep-alive-time [(.getKeepAliveTime this (to-timeunit :ms)) (to-timeunit :ms)]
     :maximum-pool-size (.getMaximumPoolSize this)
     :completed-task-count (.getCompletedTaskCount this)
     :total-task-count (.getTaskCount this)
     :active-threads (.getActiveCount this)})
  ConfigurableProcessor
  (configure [this key value]
    (case key
      :keep-alive-time (do (.setKeepAliveTime this value (to-timeunit value))
                           true)
      :maximum-pool-size (do (if (< value (.getMaximumPoolSize this))
                               (do (.setCorePoolSize this value)
                                   (.setMaximumPoolSize this value))
                               (do (.setMaximumPoolSize this value)
                                   (.setCorePoolSize this value)))
                             true)
      false)))

(defn make []
  (let [threads (.. Runtime getRuntime availableProcessors)]
    (ThreadPoolExecutor. threads
                         threads
                         1 (to-timeunit :m)
                         (LinkedBlockingQueue.))))

(defn working? [processor]
  (let [{:keys [active-threads
                completed-task-count
                total-task-count]} (status processor)]
    (or (pos? active-threads)
        (not= completed-task-count total-task-count))))
