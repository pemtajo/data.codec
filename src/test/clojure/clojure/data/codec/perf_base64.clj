(ns clojure.data.codec.perf-base64
  (:import java.io.PrintWriter org.apache.commons.codec.binary.Base64)
  (:require [clojure.java.io :as io]
            [clojure.string :as str])
  (:use clojure.data.codec.base64))

(comment ;Example usage
(gen-data-file "data.in" 1 12 20)         ; generate the data file to be used
(init-time-file "data.in" "time.out" 100) ; initialize timing file
(run-perf "data.in" "time.out" 100 false) ; add timings for (encode ...)
(run-perf "data.in" "time.out" 100 true)  ; add timings for (encode! ...)
)

(set! *warn-on-reflection* true)

(defn rand-bytes
  "Returns a randomly populated byte-array of length n."
  [n]
  (->> #(byte (- (rand-int 256) 128))
    repeatedly
    (take n)
    (byte-array)))

(defn gen-data-file
  "Prints to file randomly generated byte vectors between lengths from and to,
   inclusive, with times number of each length."
  [file from to times]
  (with-open [w (PrintWriter. (io/writer file))]
    (binding [*out* w]
      (doseq [n (range from (inc to))]
        (doseq [_ (range 0 times)]
          (println (into [] (rand-bytes n))))))))

(defn read-data-file
  "Lazily reads a data file, returning a lazy sequence of byte-arrays."
  [file]
  (->> (line-seq (io/reader file))
      (map read-string)
      (map #(map byte %))
      (map byte-array)))

(defmacro time-it
  "Like clojure.core/time, but returns the time in nanos instead of printing it."
  [expr]
  `(let [start# (System/nanoTime)
         _# ~expr
         stop# (System/nanoTime)]
     (- stop# start#)))

(defn perf-clj
  "Returns a lazy sequence of encode timings for the given sequence of byte arrays."
  [bas sleep]
  (for [ba bas]
    (do
      (Thread/sleep sleep)
      (time-it (encode ba)))))

(defn perf-clj-buf
  "Returns a lazy sequence of encode! timings for the given sequence of byte arrays."
  [bas sleep]
  (let [out (memoize (fn [n] (byte-array (enc-length n))))]
    (for [^bytes ba bas]
      (let [len (alength ba)
            output (out len)]
        (do
          (Thread/sleep sleep)
          (time-it (encode! ba 0 len output)))))))

(defn perf-apache
  "Returns a lazy sequence of apache base64 encode timings for the given sequence of
   byte arrays."
  [bas sleep]
  (for [ba bas]
    (do
      (Thread/sleep sleep)
      (time-it (Base64/encodeBase64 ba)))))

(defn append-times
  "Lazily adds a column of timings to the given table."
  [table times]
  (map (fnil conj []) table times))

(defn write-time-file
  "Writes a table of timings to file."
  [table file]
  (with-open [w (PrintWriter. (io/writer file))]
    (binding [*out* w]
      (doseq [row table]
        (print (first row))
        (doseq [e (next row)]
          (print \tab)
          (print e))
        (println)))))

(defn read-time-file
  "Lazily reads a file containing a table of timings."
  [file]
  (->> (line-seq (io/reader file))
    (map #(str/split % #"\t"))
    (map vec)))


(defn third [[_ _ x]] x)

(defn latest-time-file
  "Returns the name of the latest enumerated file with the given basis, or nil if it
   doesn't exist."
  [basis]
  (let [dir (io/file ".")
        nums (->> (.list dir )
               (filter #(.startsWith ^String % basis))
               (map #(re-matches #"(.+\.)([0-9]{3})" %))
               (keep identity)
               (map third)
               (map #(Integer/parseInt %)))]
    (if (seq nums)
      (format "%s.%03d" basis (apply max nums)))))

(defn next-time-file
  "Returns the name of the next enumerated file after the given enumerated file name."
  [file]
  (if-let [[_ prefix suffix] (re-matches #"(.+\.)([0-9]{3})" file)]
    (format "%s%03d" prefix (inc (Integer/parseInt suffix)))))

(defn init-perf-apache
  "Returns a lazy sequence of [count apache-timing] for each byte-array."
  [bas sleep]
  (map #(vector (count %1) %2) bas (perf-apache bas sleep)))

(defn init-time-file
  "Using the given data file, creates a timing file using apache base64 encoding.
   Sleep specifies the delay between iterations, thus reducing the chance of delays
   from GC affecting the timing."
  [data-file time-file sleep]
  (write-time-file (init-perf-apache (read-data-file data-file) sleep) (str time-file ".000")))

(defn run-perf
  "Using the given data file, adds a column of clojure base64 encode times to the
   specified timing file.  If use-buffer? is true, encode! will be used instead.
   Sleep specifies the delay between iterations, thus reducing the chance of delays
   from GC affecting the timing."
  [data-file time-file sleep use-buffer?]
  (let [prev-time-file (latest-time-file time-file)
        next-time-file (next-time-file prev-time-file)]
    (write-time-file
      (append-times
        (read-time-file prev-time-file)
        (if use-buffer?
          (perf-clj-buf (read-data-file data-file) sleep)
          (perf-clj (read-data-file data-file) sleep)))
      next-time-file)))


