(ns ants.core
  (:use (clojure.tools (logging :only (log)))))

(declare start!)

;;--------------------------------------------------
;; helpers

(defn- get-task!
  "Pops and returns a task form the queue if available."
  [queue-ref]
  (dosync
   (when-let [task (first @queue-ref)]
     (alter queue-ref subvec 1)
     task)))

(defn- terminate!?
  "Returns true if the worker should terminate."
  [ant]
  (dosync
   (when (or (> (-> ant :state deref :workers count)
                (-> ant :config deref :target-workers))
             (-> ant :queue deref count zero?))
     (alter (:state ant) update-in [:workers] dissoc (:id ant))
     true)))

;;--------------------------------------------------
;; worker functions

(defn- process-queue [ant]
  (when-not (terminate!? ant)
    (when-let [task (get-task! (:queue ant))]
      (log :trace (str "Processing task (" (:name task) ")"))
      (try ((::f task))
           (catch Throwable t
             (log :error (format "Failed to process task '%s': %s"
                                 (:name task)
                                 (or (.getMessage t) (.toString t) "?")))
             (dosync
              (alter (:state ant) update-in [:failed]
                     conj {:exception t :task task})))))
    (send-off *agent* process-queue))
  ant)

(defn- ensure-workers!
  "Launches additional workers if needed."
  [inst]
  (let [redo
        (dosync
         (when (and (< (-> inst :state deref :workers count)
                       (-> inst :config deref :target-workers))
                    (-> inst :queue deref count pos?))
           (let [id (gensym "worker__")
                 ant (agent (assoc inst :id id))]
             (alter (:state inst) update-in [:workers] assoc id ant)
             (send-off ant process-queue))
           true))]
    (if redo
      (recur inst)
      inst)))

;;--------------------------------------------------
;; interface

(defn create
  "Creates a new ants instance. The instance is ready to accept tasks
  and will start processing them as they are added."
  [& {:as config}]
  (let [default-ants (inc (.availableProcessors (Runtime/getRuntime)))]
    (start!
     {:queue (ref [])
      :state (ref {:workers nil
                   :failed []})
      :config (ref (merge {:max-workers default-ants
                           :target-workers default-ants}
                          config))})))

(defn start!
  "Restarts the instance if it has been previously stop!ed.

  When an instance is created it is already running so there's no need
  to call this on a newly created instance."
  [inst]
  (dosync
   (let [max (-> inst :config deref :max-workers)]
     (alter (:config inst) assoc :target-workers max)))
  (ensure-workers! inst))

(defn stop!
  "Stops processing of tasks. Tasks that are currently running will
  complete before exiting. This command may return before all worker
  threads have finished.

  This function should generally only be used if there is a need to
  pause the task processing."
  [inst]
  (dosync
   (alter (:config inst) assoc :target-workers 0))
  inst)

(defn add-task!
  "Adds a task to this instance. It will be processed at some point in
  the future unless the instance is stop!ed."
  [inst f & {:as data}]
  (dosync
   (alter (:queue inst) conj
          (assoc (merge {:name "Noname"} data) ::f f)))
  (ensure-workers! inst))

(defn finished?
  "Returns true if all tasks have been processed."
  [inst]
  (and (empty? @(:queue inst))
       (-> inst :state deref :workers count zero?)))

(defn await-empty
  "This function does not return until all tasks have been processed."
  [inst]
  (when-not (finished? inst)
    (Thread/sleep 100)
    (recur inst)))

(defn snapshot
  "Returns a snapshot of the current state."
  [inst]
  {:queue @(:queue inst)
   :failed (-> inst :state deref :failed)
   :workers-max (-> inst :config deref :max-workers)
   :workers-current (-> inst :state deref :workers count)})
