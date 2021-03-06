(ns dommy.template
  (:require [clojure.string :as str]))

(defprotocol element
  (-elem [this] "return the element representation of this"))

(defn add-class! [node c]
  (.setAttribute node "class" 
      (if-let [cur-c (.getAttribute node "class")]
        (str cur-c " " c)
        c)))

(defn style-str [m]  
  (->> m 
       (map (fn [[k v]] (str (name k) ":" (name v) ";")))
       (str/join " ")))

(defn add-attrs!
  "can have a seq for :classes key or a map for :style"
  [node attrs]
  (doseq [[k v] attrs]
    (case k
      :class (add-class! node v)
      :classes (doseq [c v] (add-class! node c))
      :style (.setAttribute node (name k) (style-str v))
      (.setAttribute node (name k) v))))

(defn next-css-index [s start-idx]
  "index of css character (#,.) in base-element. bottleneck"
  (let [id-idx (.indexOf s "#" start-idx)
        class-idx (.indexOf s "." start-idx)
        idx (.min js/Math id-idx class-idx)]
    (if (< idx 0)
      (.max js/Math id-idx class-idx)
      idx)))

(defn base-element
  "dom element from css-style keyword like :a.class1 or :span#my-span.class"
  [node-key]
  (let [node-str (name node-key)
        base-idx (next-css-index node-str 0)
        tag (cond
              (> base-idx 0) (.substring node-str 0 base-idx)
              (zero? base-idx) "div"
              :else node-str)
        node (.createElement js/document tag)]
    (when (>= base-idx 0)
      (loop [str (.substring node-str base-idx)]
        (let [next-idx (next-css-index str 1)
              frag (if (>= next-idx 0)
                     (.substring str 0 next-idx)
                     str)]
          (case (.charAt frag 0)
            \. (add-class! node (.substring frag 1))
            \# (.setAttribute node "id" (.substring frag 1)))
          (when (>= next-idx 0)
            (recur (.substring str next-idx))))))
    node))

(declare node)

(defn compound-element
  "element with either attrs or nested children [:div [:span \"Hello\"]]"
  [data]
  (let [n (base-element (first data))
        attrs (when (map? (second data)) (second data))
        tail (drop (if attrs 2 1) data) 
        ;; Remove one level of nesting for cases like [:div [[:span][:span]]]
        tail (mapcat (fn [group] (if (satisfies? element group) [group] group)) tail)]
    (when attrs 
      (add-attrs! n attrs))
    (doseq [child tail]
      (.appendChild n (node child)))
    n))

(extend-protocol element
  js/HTMLElement
  (-elem [this] this)

  Keyword
  (-elem [this] (base-element this))

  PersistentVector
  (-elem [this] (compound-element this))

  js/Text
  (-elem [this] this)

	number
  (-elem [this] (.createTextNode js/document (str this)))

	js/String
  (-elem [this]
         (if (keyword? this)
           (base-element this)
           (.createTextNode js/document (str this)))))

(defn node [data]
  (if (satisfies? element data)
    (-elem data)
    (throw (str "Don't know how to make node from " (pr-str data)))))

(defn html->nodes [html]
  (let [parent (.createElement js/document "div")]
    (.insertAdjacentHTML parent "beforeend" html)
    (->> parent .-childNodes (.call js/Array.prototype.slice))))
