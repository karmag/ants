(ns karmag.ants.protocol
  (:import java.util.concurrent.TimeUnit))

(defprotocol Processor
  (add [this task]
    "Submit a task for processing. Returns a future representing the
    task.")
  (add-all [this task-coll]
    "Submit all tasks in coll for processing. Returns a list of future
    representing the tasks.")
  (shutdown [this]
    "Finishes processing the tasks currently added but will not accept
    any new tasks.")
  (abort [this]
    "Attempts to stop any tasks currently running and any task not yet
    processed. Returns a list of runnables representing the tasks that
    were not processed.")
  (wait-for [this time timeunit]
    "Calls shutdown and blocks until all tasks have finished
    executing. Returns true if all tasks were finished and false if a
    timeout or thread interrupt occured.")
  (status [this]
    "Returns a map with information about the current state of the
    processor. What keys will be available depends on the
    implementation."))

(defprotocol ConfigurableProcessor
  (configure [this key value]
    "Returns true if the value was setup or false if the key is not
    supported for this implementation."))

(defprotocol ToTimeUnit
  (to-timeunit [this]))

(extend-protocol ToTimeUnit
  TimeUnit
  (to-timeunit [this] this)
  clojure.lang.Keyword
  (to-timeunit [this]
    (case this
      ;; regular
      :days         TimeUnit/DAYS
      :hours        TimeUnit/HOURS
      :microseconds TimeUnit/MICROSECONDS
      :milliseconds TimeUnit/MILLISECONDS
      :minutes      TimeUnit/MINUTES
      :nanoseconds  TimeUnit/NANOSECONDS
      :seconds      TimeUnit/SECONDS
      ;; abbreviations
      :h  TimeUnit/HOURS
      :ms TimeUnit/MILLISECONDS
      :m  TimeUnit/MINUTES
      :ns TimeUnit/NANOSECONDS
      :s  TimeUnit/SECONDS)))
