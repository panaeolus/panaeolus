(ns panaeolus.chords
  (:use [panaeolus.pitches]))

(set! *warn-on-reflection* true)

(def hljómasæti "7th's are Major unless indicated by flat (b) sign"
  {:i3 [0 3] :i4 [0 5]  :i5 [0 7] :im6 [0 8] :iM6 [0 9] :im7 [0 10] :iM7 [0 11] :i8 [0 12]
   :i [0 3 7] :i6 [3 7 12] :i46 [7 12 15] :i7 [0 3 7 11] :i56 [3 7 11 12] :i34 [7 11 12 15] :i24 [11 12 15 19]
   :ib7 [0 3 7 10] :ib56 [3 7 10 12] :ib34 [7 10 12 15]
   :ib24 [10 12 15 19] :isus [0 5 7] :i9 [0 3 10 14]
   :i° [0 3 6] :i°6 [3 6 12] :i°46 [6 12 15]
   :i7° [0 3 6 9] :i56° [3 6 9 12] :i34° [6 9 12 15] :i24° [9 12 15 18]
   :i°7 [0 3 6 10] :i°56 [3 6 10 12] :i°34 [6 10 12 15] :i°24 [10 12 15 18]
   :i°7+ [0 3 6 11] :i°56+ [3 6 11 12] :i°34+ [6 11 12 15] :i°24+ [11 12 15 18]
   :I3 [0 4] :I4 [0 5]  :I5 [0 7] :Im6 [0 8] :IM6 [0 9] :Im7 [0 10] :IM7 [0 11] :I8 [0 12]
   :I [0 4 7] :I6 [4 7 12] :I46 [7 12 16] :I7 [0 4 7 11] :I56 [4 7 11 12] :I34 [7 11 12 16] :I24 [11 12 16 18]
   :Ib7 [0 4 7 10] :Ib56 [4 7 10 12] :Ib34 [7 10 12 16] :Ib24 [10 12 16 19] :Isus [0 5 7] :I9 [0 4 11 14] :I9b7 [0 4 10 14]
   :I+ [0 4 8] :I+6 [4 8 12] :I+46 [8 12 16] :I+7 [0 4 8 11] :I+56 [4 8 11 12] :I+34 [8 11 12 16] :I+24 [11 12 16 20]
   :I+b7 [0 4 8 10] :I+b56 [4 8 10 12] :I+b34 [8 10 12 16] :I+b24 [10 12 16 20]})

(defn chord-midi [root mode chord]
  (let [chord             (if (vector? chord) chord [chord])
        str-fn            (map name chord)
        str-regex         (map #(java.util.regex.Pattern/compile %) str-fn)
        str-cnt-dot       (map #(* -12 %)  (for [x str-fn] (count (filter #(= \. %) (seq x)))))
        str-cnt-comma     (map #(* 12 %) (for [x str-fn] (count (filter #(= \' %) (seq x)))))
        flat-sharp-lookup (vec (for [x (range (count str-fn))] (if (= (first (nth str-fn x)) \#) 1
                                                                   (if (= (first (nth str-fn x)) \b) -1 0))))
        str-comma-dot     (doall (mapv #(+ % %2) str-cnt-dot str-cnt-comma))
        str-char          (map #(clojure.string/replace % #"\d|°|\W|b|m|M|sus" "") str-fn)
        str-str           (map str str-char)
        str-del-comma     (for [x str-fn] (apply str (remove #{\. \'} x)))
        str-del-b-s       (map #(clojure.string/replace % #"^b|^#" "") str-del-comma)
        str-convert       (for [x str-del-b-s]
                            (if (some #(Character/isUpperCase ^java.lang.Character %) x)
                              (clojure.string/replace x #"^VII|^VI|^V|^IV|^III|^II" "I")
                              (clojure.string/replace x #"^vii|^vi|^v|^iv|^iii|^ii|^i" "i")))
        str-resolve       (mapv #(% hljómasæti) (map keyword str-convert))
        hlj-map           {"i" 0 "I" 0 "ii" 1 "II" 1 "iii" 2 "III" 2 "iv" 3 "IV" 3 "v" 4 "V" 4 "vi" 5 "VI" 5 "vii" 6 "VII" 6
                           "#" 1 "b" -1}
        hlj-matz          (map hlj-map str-str)
        skali             (scale-all root mode)
        grunntonar        (mapv #(nth skali %) hlj-matz)
        grunntonar-oct    (mapv #(+ % %2) grunntonar str-comma-dot)
        utkoma            (vec (for [x (range (count grunntonar-oct))]
                                 (mapv #(+ (+ (get grunntonar-oct x) (get flat-sharp-lookup x)) %) (get str-resolve x))))]
    utkoma))

(defn chordname-scalename [chord]
  (let [chord (if (vector? chord) chord [chord])
        str-fn    (map name chord)
        str-rmv-num (map #(clojure.string/replace % #"\d+|\+|m|M|\°|b\d|[sus]" "") str-fn)]
    str-rmv-num))

(defn chord [root mode chord]
  (let [chord             (if (vector? chord) chord [chord])
        str-fn            (map name chord)
        str-regex         (map #(java.util.regex.Pattern/compile %) str-fn)
        str-cnt-dot       (map #(* -12 %)  (for [x str-fn] (count (filter #(= \. %) (seq x)))))
        str-cnt-comma     (map #(* 12 %) (for [x str-fn] (count (filter #(= \' %) (seq x)))))
        flat-sharp-lookup (vec (for [x (range (count str-fn))] (if (= (first (nth str-fn x)) \#) 1
                                                                   (if (= (first (nth str-fn x)) \b) -1 0))))
        str-comma-dot     (doall (mapv #(+ % %2) str-cnt-dot str-cnt-comma))
        str-char          (map #(clojure.string/replace % #"\d|°|\W|b|m|M|sus" "") str-fn)
        str-str           (map str str-char)
        str-del-comma     (for [x str-fn] (apply str (remove #{\. \'} x)))
        str-del-b-s       (map #(clojure.string/replace % #"^b|^#" "") str-del-comma)
        str-convert       (for [x str-del-b-s]
                            (if (some #(Character/isUpperCase ^java.lang.Character %) x)
                              (clojure.string/replace x #"^VII|^VI|^V|^IV|^III|^II" "I")
                              (clojure.string/replace x #"^vii|^vi|^v|^iv|^iii|^ii|^i" "i")))
        str-resolve       (mapv #(% hljómasæti) (map keyword str-convert))
        hlj-map           {"i" 0 "I" 0 "ii" 1 "II" 1 "iii" 2 "III" 2 "iv" 3 "IV" 3 "v" 4 "V" 4 "vi" 5 "VI" 5 "vii" 6 "VII" 6
                           "#" 1 "b" -1}
        hlj-matz          (map hlj-map str-str)
        skali             (scale-all root mode)
        grunntonar        (mapv #(nth skali %) hlj-matz)
        grunntonar-oct    (mapv #(+ % %2) grunntonar str-comma-dot)
        utkoma            (vec (for [x (range (count grunntonar-oct))]
                                 (mapv #(midi->freq (+ (+ (get grunntonar-oct x) (get flat-sharp-lookup x)) %)) (get str-resolve x))))]
    utkoma))

(defn hljómaskali
  "chord-scales"
  [root key chord]
  (let [chord             (if (vector? chord) chord [chord])
        str-fn-pre        (doall (map name chord))
        str-rmv-num       (doall (map #(clojure.string/replace % #"\d+|\+|m|M|\°|b\d|[sus]" "") str-fn-pre))
        c->s              str-rmv-num
        str-fn            (map name c->s)
        str-cnt-dot       (map #(* -12 %)  (for [x str-fn] (count (filter #(= \. %) (seq x)))))
        str-cnt-comma     (map #(* 12 %) (for [x str-fn] (count (filter #(= \' %) (seq x)))))
        flat-sharp-lookup (vec (for [x (range (count str-fn))] (if (= (first (nth str-fn x)) \#) 1
                                                                   (if (= (first (nth str-fn x)) \b) -1 0))))
        str-comma-dot     (doall (mapv #(+ % %2) str-cnt-dot str-cnt-comma))
        str-del-comma     (for [x str-fn] (apply str (remove #{\. \'} x)))
        str-del-b-s       (doall (map #(clojure.string/replace % #"^b|^#" "") str-del-comma))
        grunnsæti         {:i 0 :ii 1 :iii 2 :iv 3 :v 4 :vi 5 :vii 6
                           :I 0 :II 1 :III 2 :IV 3 :V 4 :VI 5 :VII 6}
        sæti-keyword      (doall (map keyword str-del-b-s))
        sæti-lookup       (doall (map #(% grunnsæti) sæti-keyword))
        skali             (scale-all root key)
        skali-lookup      (doall (map #(get skali %) sæti-lookup))
        krom-oct-map      (doall (mapv #(+ % %2 %3) flat-sharp-lookup str-comma-dot skali-lookup))
        mode-name-map     (list
                           (map #(if (not= nil %) (apply str "locrian")) (map #(re-find #"°" %) (seq str-fn-pre))) ;lókrískur
                           (map #(if (not= nil %) (apply str "whole")) (map #(re-find #"\+" %) (seq str-fn-pre))) ;heilt
                           (map #(if (not= nil %) (apply str "major")) (map #(re-find #"[A-Z]++(?!b|\+)" %) (seq str-fn-pre))) ;dúrst7
                           (map #(if (not= nil %) (apply str "minor")) (map #(re-find #"[c-z]++(?!b|°)" %) (seq str-fn-pre))) ;moll
                           (map #(if (not= nil %) (apply str "dorian")) (map #(re-find #"[c-z]+b" %) (seq str-fn-pre))) ;moll-l7
                           (map #(if (not= nil %) (apply str "mixolydian")) (map #(re-find #"[A-Z]+b" %) (seq str-fn-pre))))

        modn2  (doall (flatten (for [x mode-name-map]
                                 (keep-indexed vector x))))
        modn3  (doall (partition 2 modn2))
        modn4  (doall (remove nil? (for [x modn3]
                                     (if (some #(not= nil (get % 1)) x) x))))
        modn5  (doall (take (count chord) (map keyword (flatten (map #(rest %) (sort-by #(first %) modn4))))))
        skalar (doall (mapv #(scale-from-midi % %2) krom-oct-map modn5))
                                        ;utkoma-hz    (mapv #(midi->freq %) krom-oct-map)
        ]
    skalar))



(defn chord-tool [root mode chord]
  (let [chord             (if (vector? chord) chord [chord])
        str-fn            (map name chord)
        str-regex         (map #(java.util.regex.Pattern/compile %) str-fn)
        str-cnt-dot       (map #(* -12 %)  (for [x str-fn] (count (filter #(= \. %) (seq x)))))
        str-cnt-comma     (map #(* 12 %) (for [x str-fn] (count (filter #(= \' %) (seq x)))))
        flat-sharp-lookup (vec (for [x (range (count str-fn))] (if (= (first (nth str-fn x)) \#) 1
                                                                   (if (= (first (nth str-fn x)) \b) -1 0))))
        str-comma-dot     (doall (mapv #(+ % %2) str-cnt-dot str-cnt-comma))
        str-char          (map #(clojure.string/replace % #"\d|m|M|°|\W|b|sus" "") str-fn)
        str-str           (map str str-char)
        str-del-comma     (for [x str-fn] (apply str (remove #{\. \'} x)))
        str-del-b-s       (map #(clojure.string/replace % #"^b|^#" "") str-del-comma)
        str-convert       (for [x str-del-b-s]
                            (if (Character/isUpperCase ^java.lang.Character (first x))
                              (clojure.string/replace x #"^VII|^VI|^V|^IV|^III|^II" "I")
                              (clojure.string/replace x #"^vii|^vi|^v|^iv|^iii|^ii|^i" "i")))
        str-resolve       (mapv #(% hljómasæti) (map keyword str-convert))
        hlj-map           {"i" 0 "I" 0 "ii" 1 "II" 1 "iii" 2 "III" 2 "iv" 3 "IV" 3 "v" 4 "V" 4 "vi" 5 "VI" 5 "vii" 6 "VII" 6
                           "#" 1 "b" -1}
        hlj-matz          (map hlj-map str-str)
        skali             (scale-all root mode)
        grunntonar        (mapv #(nth skali %) hlj-matz)
        grunntonar-oct    (mapv #(+ % %2) grunntonar str-comma-dot)
        ;; hz                (vec (for [x (range (count grunntonar-oct))]
        ;;                          (mapv #(midi->freq (+ (+ (get grunntonar-oct x) (get flat-sharp-lookup x)) %)) (get str-resolve x))))
        midi              (vec (for [x (range (count grunntonar-oct))]
                                 (mapv #(+ (+ (get grunntonar-oct x) (get flat-sharp-lookup x)) %) (get str-resolve x))))]
    ;; {:freq hz :midi midi}
    midi))
