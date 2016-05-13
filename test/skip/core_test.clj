;; Copyright © 2016, JUXT LTD.

(ns skip.core-test
  (:require
   [skip.core :refer [add-dependency! fresh? new-file-proxy freshen]]
   [clojure.test :refer :all])
  (:import [skip.core Dependant AlwaysFresh NeverFresh]))

(deftest add-dependency-test
  (let [d (new Dependant (atom (vector (AlwaysFresh.) (AlwaysFresh.))))]
    (is (fresh? d))
    (add-dependency! d (NeverFresh.))
    (is (not (fresh? d)))))

(deftest file-proxy-test
  (let [file (java.io.File/createTempFile "skip" ".tmp")]
    (spit file "data")
    ;; Wait for at least a second because of the resolution of
    ;; .lastModified on Unix.
    (Thread/sleep 1000)
    (try
      (let [proxy (new-file-proxy file)]
        (is (fresh? proxy))
        (spit file "new data")
        (is (not (fresh? proxy)))
        (let [proxy (freshen proxy)]
          (is (fresh? proxy))))
      (finally (.delete file)))))
