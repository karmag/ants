(ns karmag.ants.core-test
  (:use clojure.test
        karmag.ants.core))

(deftest basic-usage
  (let [prc (make-processor)
        result (atom #{})]
    (add prc #(swap! result conj 100))
    (add-all prc (map (fn [i] #(swap! result conj i))
                      (range 10)))
    (wait-for prc 10 :s)
    (is (= @result (set (conj (range 10) 100))))))

(deftest basic-status-lookup
  (let [prc (make-processor)
        data (status prc)]
    (is (pos? (get data :maximum-pool-size)))
    (doseq [key [:completed-task-count :total-task-count]]
      (is (zero? (get data key))))
    (let [[time timeunit] (get data :keep-alive-time)]
      (is (and time timeunit))
      (is (pos? time)))))

(deftest processed-tasks-are-counted
  (let [prc (make-processor)]
    (configure prc :maximum-pool-size 1)
    (add-all prc (repeat 5 (fn [])))
    (add-all prc (repeat 5 (fn [] (Thread/sleep 1000))))
    (Thread/sleep 500)
    (let [{:keys [total-task-count completed-task-count]}
          (status prc)]
      (is (= 10 total-task-count))
      (is (= 5 completed-task-count)))
    (abort prc)
    (wait-for prc 10 :s)
    (let [{:keys [total-task-count completed-task-count]}
          (status prc)]
      (is (= 6 total-task-count))
      (is (= 6 completed-task-count)))))

(deftest optimizer-should-not-crash
  (let [prc (make-processor)]
    (configure prc :maximum-pool-size 1)
    (add-all prc (repeat 10000 #(Thread/sleep 100)))
    (let [optimizer (start-optimizer prc)]
      (wait-for prc 1 :s)
      (release-optimizer optimizer))))

(deftest wait-while-works
  (let [prc (make-processor)]
    (configure prc :maximum-pool-size 1)
    (add-all prc (repeat 10 #(Thread/sleep 250)))
    (wait-while prc :interval [100 :ms])
    (is (= 10 (:completed-task-count (status prc))))))
