(ns clojure.data.codec.test-base64
  (:import org.apache.commons.codec.binary.Base64)
  (:use clojure.test
        clojure.data.codec.base64))

(defn rand-bytes [n]
  (->> #(byte (- (rand-int 256) 128))
    repeatedly
    (take n)
    (byte-array)))

(deftest correctness
  (doseq [n (range 1 100)]
    (is (let [input (rand-bytes n)
              a1 (encode input)
              a2 (Base64/encodeBase64 input)]
          (= (into [] a1) (into [] a2))))))

(deftest offset-correctness
  (doseq [n (range 1 100)]
    (doseq [off (range 1 n)]
      (is (let [input (rand-bytes n)
                len (- n off)
                a1 (encode input off len)
                input2 (byte-array len)
                _ (System/arraycopy input off input2 0 len)
                a2 (Base64/encodeBase64 input2)]
            (= (into [] a1) (into [] a2)))))))