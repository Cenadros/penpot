;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.
;;
;; Copyright (c) KALEIDOS INC

(ns app.common.geom.shapes.layout
  (:require
   [app.common.data :as d]
   [app.common.geom.point :as gpt]
   [app.common.geom.shapes.rect :as gsr]
   [app.common.geom.shapes.transforms :as gst]))

;; :layout                 ;; true if active, false if not
;; :layout-dir             ;; :right, :left, :top, :bottom
;; :layout-gap             ;; number could be negative
;; :layout-type            ;; :packed, :space-between, :space-around
;; :layout-wrap-type       ;; :wrap, :no-wrap
;; :layout-padding-type    ;; :simple, :multiple
;; :layout-padding         ;; {:p1 num :p2 num :p3 num :p4 num} number could be negative
;; :layout-h-orientation   ;; :top, :center, :bottom
;; :layout-v-orientation   ;; :left, :center, :right

(defn col?
  [{:keys [layout-dir]}]
  (or (= :right layout-dir) (= :left layout-dir)))

(defn row?
  [{:keys [layout-dir]}]
  (or (= :top layout-dir) (= :bottom layout-dir)))

(defn h-start?
  [{:keys [layout-h-orientation]}]
  (= layout-h-orientation :left))

(defn h-center?
  [{:keys [layout-h-orientation]}]
  (= layout-h-orientation :center))

(defn h-end?
  [{:keys [layout-h-orientation]}]
  (= layout-h-orientation :right))

(defn v-start?
  [{:keys [layout-v-orientation]}]
  (= layout-v-orientation :top))

(defn v-center?
  [{:keys [layout-v-orientation]}]
  (= layout-v-orientation :center))

(defn v-end?
  [{:keys [layout-v-orientation]}]
  (= layout-v-orientation :bottom))

(defn add-padding [transformed-rect {:keys [layout-padding-type layout-padding]}]
  (let [{:keys [p1 p2 p3 p4]} layout-padding
        [p1 p2 p3 p4]
        (if (= layout-padding-type :multiple)
          [p1 p2 p3 p4]
          [p1 p1 p1 p1])]

    (-> transformed-rect
        (update :y + p1)
        (update :width - p2 p3)
        (update :x + p3)
        (update :height - p1 p4))))

;; FUNCTIONS TO WORK WITH POINTS SQUARES

(defn origin
  [points]
  (nth points 0))

(defn start-hv
  "Horizontal vector from the origin with a magnitude `val`"
  [[p0 p1 _ _] val]
  (-> (gpt/to-vec p0 p1)
      (gpt/unit)
      (gpt/scale val)))

(defn end-hv
  "Horizontal vector from the oposite to the origin in the x axis with a magnitude `val`"
  [[p0 p1 _ _] val]
  (-> (gpt/to-vec p1 p0)
      (gpt/unit)
      (gpt/scale val)))

(defn start-vv
  "Vertical vector from the oposite to the origin in the x axis with a magnitude `val`"
  [[p0 _ _ p3] val]
  (-> (gpt/to-vec p0 p3)
      (gpt/unit)
      (gpt/scale val)))

(defn end-vv
  "Vertical vector from the oposite to the origin in the x axis with a magnitude `val`"
  [[p0 _ _ p3] val]
  (-> (gpt/to-vec p3 p0)
      (gpt/unit)
      (gpt/scale val)))

;;(defn start-hp
;;  [[p0 _ _ _ :as points] val]
;;  (gpt/add p0 (start-hv points val)))
;;
;;(defn end-hp
;;  "Horizontal Vector from the oposite to the origin in the x axis with a magnitude `val`"
;;  [[_ p1 _ _ :as points] val]
;;  (gpt/add p1 (end-hv points val)))
;;
;;(defn start-vp
;;  "Vertical Vector from the oposite to the origin in the x axis with a magnitude `val`"
;;  [[p0 _ _ _ :as points] val]
;;  (gpt/add p0 (start-vv points val)))
;;
;;(defn end-vp
;;  "Vertical Vector from the oposite to the origin in the x axis with a magnitude `val`"
;;  [[_ _ p3 _ :as points] val]
;;  (gpt/add p3 (end-vv points val)))

(defn width-points
  [[p0 p1 _ _]]
  (gpt/length (gpt/to-vec p0 p1)))

(defn height-points
  [[p0 _ _ p3]]
  (gpt/length (gpt/to-vec p0 p3)))

(defn pad-points
  [[p0 p1 p2 p3 :as points] pad-top pad-right pad-bottom pad-left]
  (let [top-v    (start-vv points pad-top)
        right-v  (end-hv points pad-right)
        bottom-v (end-vv points pad-bottom)
        left-v   (start-hv points pad-left)]

    [(-> p0 (gpt/add left-v)  (gpt/add top-v))
     (-> p1 (gpt/add right-v) (gpt/add top-v))
     (-> p2 (gpt/add right-v) (gpt/add bottom-v))
     (-> p3 (gpt/add left-v)  (gpt/add bottom-v))]))

;;;;


(defn calc-layout-lines
  [{:keys [layout-gap layout-wrap-type] :as parent} children layout-bounds]

  (let [wrap? (= layout-wrap-type :wrap)
        layout-width (width-points layout-bounds)
        layout-height (height-points layout-bounds)

        reduce-fn
        (fn [[{:keys [line-width line-height num-children line-fill? child-fill? num-child-fill] :as line-data} result] child]
          (let [child-bounds (gst/parent-coords-points child parent)
                child-width  (width-points child-bounds)
                child-height (height-points child-bounds)

                col? (col? parent)
                row? (row? parent)

                cur-child-fill?
                (or (and col? (= :fill (:layout-h-behavior child)))
                    (and row? (= :fill (:layout-v-behavior child))))

                cur-line-fill?
                (or (and row? (= :fill (:layout-h-behavior child)))
                    (and col? (= :fill (:layout-v-behavior child))))

                ;; TODO LAYOUT: ADD MINWIDTH/HEIGHT
                next-width   (if (or (and col? cur-child-fill?)
                                     (and row? cur-line-fill?))
                               0
                               child-width)

                next-height  (if (or (and row? cur-child-fill?)
                                     (and col? cur-line-fill?))
                               0
                               child-height)

                next-total-width (+ line-width next-width (* layout-gap (dec num-children)))
                next-total-height (+ line-height next-height (* layout-gap (dec num-children)))]

            (if (and (some? line-data)
                     (or (not wrap?)
                         (and col? (<= next-total-width layout-width))
                         (and row? (<= next-total-height layout-height))))

              ;; When :fill we add min width (0 by default)
              [{:line-width     (if col? (+ line-width next-width) (max line-width next-width))
                :line-height    (if row? (+ line-height next-height) (max line-height next-height))
                :num-children   (inc num-children)
                :child-fill?    (or cur-child-fill? child-fill?)
                :line-fill?     (or cur-line-fill? line-fill?)
                :num-child-fill (cond-> num-child-fill cur-child-fill? inc)}
               result]

              [{:line-width     next-width
                :line-height    next-height
                :num-children   1
                :child-fill?    cur-child-fill?
                :line-fill?     cur-line-fill?
                :num-child-fill (if cur-child-fill? 1 0)}
               (cond-> result (some? line-data) (conj line-data))])))

        [line-data layout-lines] (reduce reduce-fn [nil []] children)]

    (cond-> layout-lines (some? line-data) (conj line-data))))

(defn calc-layout-lines-position
  [{:keys [layout-gap] :as parent} layout-bounds layout-lines]

  (let [layout-width   (width-points layout-bounds)
        layout-height  (height-points layout-bounds)
        row?           (row? parent)
        col?           (col? parent)
        h-center?      (h-center? parent)
        h-end?         (h-end? parent)
        v-center?      (v-center? parent)
        v-end?         (v-end? parent)]

    (letfn [;; short version to not repeat always with all arguments
            (xv [val]
              (start-hv layout-bounds val))

            ;; short version to not repeat always with all arguments
            (yv [val]
              (start-vv layout-bounds val))

            (get-base-line
              [total-width total-height]

              (cond-> (origin layout-bounds)
                (and row? h-center?)
                (gpt/add (xv (/ (- layout-width total-width) 2)))

                (and row? h-end?)
                (gpt/add (xv (- layout-width total-width)))

                (and col? v-center?)
                (gpt/add (yv (/ (- layout-height total-height) 2)))

                (and col? v-end?)
                (gpt/add (yv (- layout-height total-height)))))

            (get-start-line
              [{:keys [line-width line-height num-children child-fill?]} base-p]

              (let [children-gap (* layout-gap (dec num-children))

                    line-width  (if (and col? child-fill?)
                                  (- layout-width (* layout-gap (dec num-children)))
                                  line-width)

                    line-height (if (and row? child-fill?)
                                  (- layout-height (* layout-gap (dec num-children)))
                                  line-height)

                    start-p
                    (cond-> base-p
                      ;; X AXIS
                      (and col? h-center?)
                      (-> (gpt/add (xv (/ layout-width 2)))
                          (gpt/subtract (xv (/ (+ line-width children-gap) 2))))

                      (and col? h-end?)
                      (-> (gpt/add (xv layout-width))
                          (gpt/subtract (xv (+ line-width children-gap))))

                      (and row? h-center?)
                      (gpt/add (xv (/ line-width 2)))

                      (and row? h-end?)
                      (gpt/add (xv line-width))

                      ;; Y AXIS
                      (and row? v-center?)
                      (-> (gpt/add (yv (/ layout-height 2)))
                          (gpt/subtract (yv (/ (+ line-height children-gap) 2))))

                      (and row? v-end?)
                      (-> (gpt/add (yv layout-height))
                          (gpt/subtract (yv (+ line-height children-gap))))

                      (and col? v-center?)
                      (gpt/add (yv (/ line-height 2)))

                      (and col? v-end?)
                      (gpt/add (yv line-height)))]

                start-p))

            (get-next-line
              [{:keys [line-width line-height]} base-p]

              (cond-> base-p
                row?
                (gpt/add (xv (+ line-width layout-gap)))

                col?
                (gpt/add (yv (+ line-height layout-gap)))))

            (add-lines [[total-width total-height] {:keys [line-width line-height]}]
              [(+ total-width line-width)
               (+ total-height line-height)])

            (add-starts [[result base-p] layout-line]
              (let [start-p (get-start-line layout-line base-p)
                    next-p  (get-next-line layout-line base-p)]
                [(conj result
                       (assoc layout-line :start-p start-p))
                 next-p]))]

      (let [[total-width total-height] (->> layout-lines (reduce add-lines [0 0]))

            total-width (+ total-width (* layout-gap (dec (count layout-lines))))
            total-height (+ total-height (* layout-gap (dec (count layout-lines))))

            vertical-fill-space (- layout-height total-height)
            horizontal-fill-space (- layout-width total-width)
            num-line-fill (count (->> layout-lines (filter :line-fill?)))

            layout-lines
            (->> layout-lines
                 (mapv #(cond-> %
                          (and col? (:line-fill? %))
                          (update :line-height + (/ vertical-fill-space num-line-fill))

                          (and row? (:line-fill? %))
                          (update :line-width + (/ horizontal-fill-space num-line-fill)))))

            total-height (if (and col? (> num-line-fill 0)) layout-height total-height)
            total-width (if (and row? (> num-line-fill 0)) layout-width total-width)

            base-p (get-base-line total-width total-height)

            [layout-lines _ _ _ _]
            (reduce add-starts [[] base-p] layout-lines)]
        layout-lines))))

(defn calc-layout-line-data
  "Calculates the baseline for a flex layout"
  [{:keys [layout-type layout-gap] :as shape}
   layout-bounds
   {:keys [num-children line-width line-height child-fill?] :as line-data}]

  (let [width (width-points layout-bounds)
        height (height-points layout-bounds)

        layout-gap
        (cond
          (or (= :packed layout-type) child-fill?)
          layout-gap

          (= :space-around layout-type)
          0

          (and (col? shape) (= :space-between layout-type))
          (/ (- width line-width) (dec num-children))

          (and (row? shape) (= :space-between layout-type))
          (/ (- height line-height) (dec num-children)))

        margin-x
        (if (and (col? shape) (= :space-around layout-type))
          (/ (- width line-width) (inc num-children))
          0)

        margin-y
        (if (and (row? shape) (= :space-around layout-type))
          (/ (- height line-height) (inc num-children))
          0)]

    (assoc line-data
           :layout-bounds layout-bounds
           :layout-gap layout-gap
           :margin-x margin-x
           :margin-y margin-y)))

(defn next-p
  "Calculates the position for the current shape given the layout-data context"
  [parent
   child-width child-height
   {:keys [start-p layout-gap margin-x margin-y] :as layout-data}]

  (let [row?      (row? parent)
        col?      (col? parent)

        layout-type (:layout-type parent)
        space-around? (= :space-around layout-type)
        space-between? (= :space-between layout-type)

        stretch-h? (and row? (or space-around? space-between?))
        stretch-v? (and col? (or space-around? space-between?))

        h-center? (and (h-center? parent) (not stretch-h?))
        h-end?    (and (h-end? parent) (not stretch-h?))
        v-center? (and (v-center? parent) (not stretch-v?))
        v-end?    (and (v-end? parent) (not stretch-v?))
        points    (:points parent)

        xv (partial start-hv points)
        yv (partial start-vv points)

        corner-p
        (cond-> start-p
          (and row? h-center?)
          (gpt/add (xv (- (/ child-width 2))))

          (and row? h-end?)
          (gpt/add (xv (- child-width)))

          (and col? v-center? (not space-around?))
          (gpt/add (yv (- (/ child-height 2))))

          (and col? v-end? (not space-around?))
          (gpt/add (yv (- child-height)))

          (some? margin-x)
          (gpt/add (xv margin-x))

          (some? margin-y)
          (gpt/add (yv margin-y)))

        next-p
        (cond-> start-p
          col?
          (gpt/add (xv (+ child-width layout-gap)))

          row?
          (gpt/add (yv (+ child-height layout-gap)))

          (some? margin-x)
          (gpt/add (xv margin-x))

          (some? margin-y)
          (gpt/add (yv margin-y)))

        layout-data
        (assoc layout-data :start-p next-p)]

    [corner-p layout-data]))

(defn calc-fill-width-data
  "Calculates the size and modifiers for the width of an auto-fill child"
  [{:keys [layout-gap transform transform-inverse] :as parent}
   {:keys [layout-h-behavior] :as child}
   child-origin child-width
   {:keys [num-children line-width line-fill? child-fill? layout-bounds] :as layout-data}]

  (cond
    (and (col? parent) (= :fill layout-h-behavior) child-fill?)
    (let [layout-width (width-points layout-bounds)
          fill-space (- layout-width line-width (* layout-gap (dec num-children)))
          fill-width (/ fill-space (:num-child-fill layout-data))
          fill-scale (/ fill-width child-width)]
      {:width fill-width
       :modifiers [{:type :resize
                    :origin child-origin
                    :transform transform
                    :transform-inverse transform-inverse
                    :vector (gpt/point fill-scale 1)}]})

    (and (row? parent) (= :fill layout-h-behavior) line-fill?)
    (let [fill-scale (/ line-width child-width)]
      {:width line-width
       :modifiers [{:type :resize
                    :origin child-origin
                    :transform transform
                    :transform-inverse transform-inverse
                    :vector (gpt/point fill-scale 1)}]})
    ))

(defn calc-fill-height-data
  "Calculates the size and modifiers for the height of an auto-fill child"
  [{:keys [layout-gap transform transform-inverse] :as parent}
   {:keys [layout-v-behavior] :as child}
   child-origin child-height
   {:keys [num-children line-height layout-bounds line-fill? child-fill?] :as layout-data}]

  (cond
    (and (row? parent) (= :fill layout-v-behavior) child-fill?)
    (let [layout-height (height-points layout-bounds)
          fill-space (- layout-height line-height (* layout-gap (dec num-children)))
          fill-height (/ fill-space (:num-child-fill layout-data))
          fill-scale (/ fill-height child-height)]
      {:height fill-height
       :modifiers [{:type :resize
                    :origin child-origin
                    :transform transform
                    :transform-inverse transform-inverse
                    :vector (gpt/point 1 fill-scale)}]})

    (and (col? parent) (= :fill layout-v-behavior) line-fill?)
    (let [fill-scale (/ line-height child-height)]
      {:height line-height
       :modifiers [{:type :resize
                    :origin child-origin
                    :transform transform
                    :transform-inverse transform-inverse
                    :vector (gpt/point 1 fill-scale)}]})
    ))

(defn normalize-child-modifiers
  "Apply the modifiers and then normalized them against the parent coordinates"
  [parent child modifiers transformed-parent]

  (let [transformed-child (gst/transform-shape child modifiers)
        child-bb-before (gst/parent-coords-rect child parent)
        child-bb-after  (gst/parent-coords-rect transformed-child transformed-parent)
        scale-x (/ (:width child-bb-before) (:width child-bb-after))
        scale-y (/ (:height child-bb-before) (:height child-bb-after))]
    (-> modifiers
        (update :v2 #(conj %
                           {:type :resize
                            :transform (:transform transformed-parent)
                            :transform-inverse (:transform-inverse transformed-parent)
                            :origin (-> transformed-parent :points (nth 0))
                            :vector (gpt/point scale-x scale-y)})))))

(defn calc-layout-data
  "Digest the layout data to pass it to the constrains"
  [{:keys [layout-dir layout-padding layout-padding-type] :as parent} children]

  (let [;; Add padding to the bounds
        {pad-top :p1 pad-right :p2 pad-bottom :p3 pad-left :p4} layout-padding
        [pad-top pad-right pad-bottom pad-left]
        (if (= layout-padding-type :multiple)
          [pad-top pad-right pad-bottom pad-left]
          [pad-top pad-top pad-top pad-top])

        ;; Normalize the points to remove flips
        points (gst/parent-coords-points parent parent)

        layout-bounds (pad-points points pad-top pad-right pad-bottom pad-left)

        ;; Reverse
        reverse? (or (= :left layout-dir) (= :bottom layout-dir))
        children (cond->> children reverse? reverse)

        ;; Creates the layout lines information
        layout-lines
        (->> (calc-layout-lines parent children layout-bounds)
             (calc-layout-lines-position parent layout-bounds)
             (map (partial calc-layout-line-data parent layout-bounds)))]

    {:layout-lines layout-lines
     :reverse? reverse?}))

(defn calc-layout-modifiers
  "Calculates the modifiers for the layout"
  [parent child layout-line]
  (let [child-bounds (gst/parent-coords-points child parent)

        child-origin (origin child-bounds)
        child-width  (width-points child-bounds)
        child-height (height-points child-bounds)

        fill-width   (calc-fill-width-data parent child child-origin child-width layout-line)
        fill-height  (calc-fill-height-data parent child child-origin child-height layout-line)

        child-width (or (:width fill-width) child-width)
        child-height (or (:height fill-height) child-height)

        [corner-p layout-line] (next-p parent child-width child-height layout-line)

        move-vec (gpt/to-vec child-origin corner-p)

        modifiers
        (-> []
            (cond-> fill-width (d/concat-vec (:modifiers fill-width)))
            (cond-> fill-height (d/concat-vec (:modifiers fill-height)))
            (conj {:type :move :vector move-vec}))]

    [modifiers layout-line]))


(defn drop-areas
  [{:keys [margin-x margin-y] :as frame} layout-data children]

  (let [col? (col? frame)
        row? (row? frame)
        h-center? (and row? (h-center? frame))
        h-end? (and row? (h-end? frame))
        v-center? (and col? (v-center? frame))
        v-end? (and row? (v-end? frame))
        layout-gap (:layout-gap frame 0)

        children (vec (cond->> children
                        (:reverse? layout-data) reverse))

        redfn-child
        (fn [[result parent-rect prev-x prev-y] [child next]]
          (let [prev-x (or prev-x (:x parent-rect))
                prev-y (or prev-y (:y parent-rect))
                last? (nil? next)

                box-x (-> child :selrect :x)
                box-y (-> child :selrect :y)
                box-width (-> child :selrect :width)
                box-height(-> child :selrect :height)

                
                x (if row? (:x parent-rect) prev-x)
                y (if col? (:y parent-rect) prev-y)

                width (cond
                        (and col? last?)
                        (- (+ (:x parent-rect) (:width parent-rect)) x)
                        
                        row?
                        (:width parent-rect)

                        :else
                        (+ box-width (- box-x prev-x) (/ layout-gap 2)))

                height (cond
                         (and row? last?)
                         (- (+ (:y parent-rect) (:height parent-rect)) y)
                         
                         col?
                         (:height parent-rect)

                         :else
                         (+ box-height (- box-y prev-y) (/ layout-gap 2)))
                
                line-area (gsr/make-rect x y width height)
                result (conj result line-area)]

            [result parent-rect (+ x width) (+ y height)]))
        
        redfn-lines
        (fn [[result from-idx prev-x prev-y] [{:keys [start-p layout-gap num-children line-width line-height]} next]]
          (let [prev-x (or prev-x (:x frame))
                prev-y (or prev-y (:y frame))
                last? (nil? next)

                line-width
                (if col?
                  (:width frame)
                  (+ line-width margin-x
                     (if col? (* layout-gap (dec num-children)) 0)))

                line-height
                (if row?
                  (:height frame)
                  (+ line-height margin-y
                     (if row?
                       (* layout-gap (dec num-children))
                       0)))

                box-x
                (- (:x start-p)
                   (cond
                     h-center? (/ line-width 2)
                     h-end? line-width
                     :else 0))

                box-y
                (- (:y start-p)
                   (cond
                     v-center? (/ line-height 2)
                     v-end? line-height
                     :else 0))

                x (if col? (:x frame) prev-x)
                y (if row? (:y frame) prev-y)

                width (cond
                        (and row? last?)
                        (- (+ (:x frame) (:width frame)) x)
                        
                        col?
                        (:width frame)

                        :else
                        (+ line-width (- box-x prev-x) (/ layout-gap 2)))

                height (cond
                         (and col? last?)
                         (- (+ (:y frame) (:height frame)) y)
                         
                         row?
                         (:height frame)

                         :else
                         (+ line-height (- box-y prev-y) (/ layout-gap 2)))

                line-area (gsr/make-rect x y width height)

                children (subvec children from-idx (+ from-idx num-children))


                ;; To debug the lines
                ;;result (conj result line-area)

                result (first (reduce redfn-child [result line-area] (d/with-next children)))]

            [result (+ from-idx num-children) (+ x width) (+ y height)]))

        ret (first (reduce redfn-lines [[] 0] (d/with-next (:layout-lines layout-data))))
        ]


    ;;(.log js/console "RET" (clj->js ret))
    ret

    ))
