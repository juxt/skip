;; Copyright © 2016, JUXT LTD.

(ns skip.core-test
  (:require
   [skip.core :refer [add-dependency! stale? new-file-proxy refresh!]]
   [clojure.test :refer :all])
  (:import [skip.core Dependant AlwaysFresh NeverFresh]))

(deftest add-dependency-test
  (let [d (new Dependant (atom (vector (AlwaysFresh.) (AlwaysFresh.))) nil)]
    (is (not (stale? d)))
    (add-dependency! d (NeverFresh.))
    (is (stale? d))))

(deftest file-proxy-test
  (let [file (java.io.File/createTempFile "skip" ".tmp")]
    (spit file "data")
    ;; Wait for at least a second because of the resolution of
    ;; .lastModified on Unix.
    (Thread/sleep 1000)
    (try
      (let [proxy (new-file-proxy file)]
        (is (not (stale? proxy)))
        (spit file "new data")
        (is (stale? proxy))
        (refresh! proxy)
        (is (not (stale? proxy))))
      (finally (.delete file)))))
