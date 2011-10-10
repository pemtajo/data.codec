(ns clojure.data.codec.base64)

(def ^:private ^"[B" enc-bytes
  (byte-array
    (map (comp byte int)
      "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/")))

(defn enc-length
  "Calculates what would be the length after encoding of an input array of length n."
  ^long [^long n]
  (-> n
    (unchecked-add 2)
    (quot 3)
    (unchecked-multiply 4)))

(defn encode!
  "Reads from the input byte array for the specified length starting at the offset
   index, and base64 encodes into the output array starting at index 0."
  [^bytes input ^long offset ^long length ^bytes output]
  (let [tail-len (unchecked-remainder-int length 3)
        loop-lim (unchecked-subtract (unchecked-add offset length) tail-len)
        end (unchecked-dec (unchecked-add offset length))]
    (loop [i offset j 0]
      (when (< i loop-lim)
        (let [x (long (aget input i)) ; can only bind long/double prims, and no widening conversion
              y (long (aget input (unchecked-inc i)))
              z (long (aget input (unchecked-add 2 i)))
              a (-> x
                  (bit-shift-right 2)
                  (bit-and 0x3F))
              b1 (-> x
                   (bit-and 0x3)
                   (bit-shift-left 4))
              b2 (-> y
                   (bit-shift-right 4)
                   (bit-and 0xF))
              b (bit-or b1 b2)
              c1 (-> y
                   (bit-and 0xF)
                   (bit-shift-left 2))
              c2 (-> z
                   (bit-shift-right 6)
                   (bit-and 0x3))
              c (bit-or c1 c2)
              d (bit-and z 0x3F)]
          (aset output j (aget enc-bytes a))
          (aset output (unchecked-inc j) (aget enc-bytes b))
          (aset output (unchecked-add 2 j) (aget enc-bytes c))
          (aset output (unchecked-add 3 j) (aget enc-bytes d)))
        (recur (unchecked-add 3 i) (unchecked-add 4 j))))
    ; add padding:
    (case tail-len
      0 output
      1 (do
          (aset output
                (unchecked-subtract (alength output) 4)
                (aget enc-bytes
                      (-> (aget input end)
                        int
                        (bit-shift-right 2)
                        (bit-and 0x3F))))
          (aset output
                (unchecked-subtract (alength output) 3)
                (aget enc-bytes
                      (-> (aget input end)
                        int
                        (bit-and 0x3)
                        (bit-shift-left 4))))
          (aset output (unchecked-subtract (alength output) 2) (byte 61))
          (aset output (unchecked-dec (alength output)) (byte 61))
          output)
      2 (do
          (aset output
                (unchecked-subtract (alength output) 4)
                (aget enc-bytes
                      (-> (aget input (unchecked-dec end))
                        int
                        (bit-shift-right 2)
                        (bit-and 0x3F))))
          (aset output
                (unchecked-subtract (alength output) 3)
                (aget enc-bytes
                      (-> (aget input (unchecked-dec end))
                        int
                        (bit-and 0x3)
                        (bit-shift-left 4)
                        (bit-or (-> (aget input end)
                                  int
                                  (bit-shift-right 4)
                                  (bit-and 0xF))))))
          (aset output
                (unchecked-subtract (alength output) 2)
                (aget enc-bytes
                      (-> (aget input end)
                        int
                        (bit-and 0xF)
                        (bit-shift-left 2))))
          (aset output (unchecked-dec (alength output)) (byte 61))
          nil))))

(defn encode
  "Returns a base64 encoded byte array."
  ([^bytes input]
    (encode input 0 (alength input)))
  ([^bytes input ^long offset ^long length]
    (let [dest (byte-array (enc-length length))]
      (encode! input offset length dest)
      dest)))
