(ns ants.core-test
  (:use clojure.test
        ants.core))

(def ^:dynamic *inst*)
(def ^:dynamic *result*)

(defmacro with-ants [& body]
  `(let [~'inst (create)]
     (binding [*inst* ~'inst
               *result* (atom 0)]
       ~@body)))

(defn do-task!
  ([]
     (do-task! 0))
  ([ms]
     (add-task! *inst*
                (fn []
                  (Thread/sleep ms)
                  (swap! *result* inc))
                :name (format "%d ms task" ms))))

(defn do-many-tasks! [n]
  (dotimes [_ n]
    (do-task! 10)))

;;--------------------------------------------------
;; tests

(deftest all-work-is-performed
  (with-ants
    (do-many-tasks! 10)
    (await-empty inst)
    (is (= 10 @*result*))))

(deftest await-waits
  (with-ants
    (do-task! 1000)
    (await-empty inst)
    (is (= 1 @*result*))))

(deftest stop-pauses-workers
  (with-ants
    (stop! inst)
    (do-many-tasks! 10)
    (Thread/sleep 1000)
    (is (= 0 @*result*))
    (start! inst)
    (await-empty inst)
    (is (= 10 @*result*))))

(deftest workers-are-relaunched-when-needed
  (with-ants
    (do-task!)
    (await-empty inst)
    (do-task!)
    (await-empty inst)
    (is (= 2 @*result*))))

(deftest tasks-can-launch-other-tasks-without-stopping
  (with-ants
    (add-task! inst
               (fn []
                 (add-task! inst
                            (fn []
                              (Thread/sleep 1000)
                              (swap! *result* inc))
                            :name "Sub task")
                 (swap! *result* inc))
               :name "Main task")
    (await-empty inst)
    (is (= 2 @*result*))))

(deftest failing-tasks-are-logged
  (with-ants
    (add-task! inst
               (fn [] (/ 1 0))
               :name "Flunky")
    (await-empty inst)
    (is (= 1 (-> inst snapshot :failed count)))))

(deftest snapshot-reports-workers
  (with-ants
    (is (-> inst snapshot :workers-max integer?))
    (is (-> inst snapshot :workers-current integer?))))
