(ns com.phronemophobic.clj-libretro.ai
  (:require [membrane.skia :as skia]
            [membrane.ui :as ui]
            [clojure.java.io :as io]
            [membrane.component :as component
             :refer [defui defeffect]]
            [membrane.basic-components :as basic ]
            [clojure.math.combinatorics :as combo]
            [com.phronemophobic.clj-libretro.constants :as c]
            [com.phronemophobic.clj-libretro.raw :as retro]
            [com.phronemophobic.clj-libretro.skia :as skia-ui]
            [clojure.data.priority-map :refer [priority-map]])
  (:import com.sun.jna.Memory
           com.sun.jna.ptr.ByteByReference
           java.io.ByteArrayOutputStream))

(retro/import-structs!)

;; helpers

(defn ^:private byte-format [b]
  (bit-and 0xFF
           b))

(defn ^:private write-string [struct field s]
  (let [bytes (.getBytes s "utf-8")
        mem (doto (Memory. (inc (alength bytes)))
              (.write 0 bytes 0 (alength bytes))
              (.setByte (alength bytes) 0))
        bbr (doto (ByteByReference.)
              (.setPointer mem))]
    (doto struct
      (.writeField field bbr))))

(defn ^:private game-info [path]
  (let [game (retro_game_infoByReference.)]
    (doto game
      (write-string "path" path)
      (.writeField "data" nil)
      (.writeField "size" 0)
      (.writeField "meta" nil))))

(defn ^:private load-save
  "Read the serialized game state from the file path `fname` and overwrite the current global game state."
  [fname]
  (let [baos (ByteArrayOutputStream.)]
    (with-open [is (io/input-stream fname)]
      (io/copy is baos))
    (let [buf (.toByteArray baos)]
      (retro/retro_unserialize buf (alength buf)))))

(defn ^:private save-game
  "Serialize the current global game state to the file path `fname`."
  [fname]
  (let [size (retro/retro_serialize_size)
        mem (Memory. size)]
    (retro/retro_serialize mem size)
    (let [buf (byte-array size)]
      (.read mem 0 buf 0 size)
      (with-open [os (io/output-stream fname)]
        (io/copy buf
                 os)))
    ))


;; callbacks

(defn ^:private set-environment [cmd data]
  (case (c/name cmd)
    :RETRO_ENVIRONMENT_SET_CONTENT_INFO_OVERRIDE 1

    :RETRO_ENVIRONMENT_GET_AUDIO_VIDEO_ENABLE
    (do
      ;; (.setInt data 0 (bit-or 1 2))
      0)

    ;; else
    0))

(defn ^:private video-refresh-callback [data width height pitch])

(defn ^:private audio-sample [left right])

(defn ^:private audio-sample-batch-callback [data num_frames]
  num_frames)

(defn input-poll []
  ;; do nothing
  )

(defn ^:private input-state-callback [port device index id]
  0)

;; end callbacks

(defn ^:private get-state
  "Return a byte array containing the serialized state of the current global game state."
  []
  (let [size (retro/retro_serialize_size)
        mem (Memory. size)]
    (retro/retro_serialize mem size)
    (let [buf (byte-array size)]
      (.read mem 0 buf 0 size)
      buf)))

(defn ^:private load-state
  "Overwrite the current global game state with the contents of `state`.

  `state` should be a byte array containing the serialized game state."
  [state]
  (retro/retro_unserialize state (alength state)))

(def ^:private render-frame @#'skia-ui/render-frame)

(defn ^:private screenshot
  "Run the current game state one frame with no inputs and return a membrane view of the screen."
  []
  (let [pm (volatile! nil)]
    (with-redefs [video-refresh-callback (fn [data width height pitch]
                                           (vreset! pm (render-frame nil data width height pitch)))]
      (retro/retro_run)
      @pm)))


(def ^:private repaint! @#'skia/glfw-post-empty-event)

(def ^:private stats
  "Store stats to show current state of search."
  (atom {}))

(def ^:private state-cache
  "Cache of input sequences to their resulting game states."
  (atom {}))


(defn ^:private next-state
  "Given a sequence of past inputs, run the current game for `num-frames` with `controls` inputs set."
  [inputs controls num-frames]
  (let [state (get @state-cache inputs)]
    (assert state)
    (load-state state))
  (with-redefs [input-state-callback
                (fn [port device index id]
                  (if (= port 0)
                    (if (controls (c/device-name id))
                      1
                      0)
                    0))
                ;; Update stats with latest screenshots
                video-refresh-callback (fn [data width height pitch]
                                         (swap! stats
                                                (fn [stats]
                                                  (let [ssn (mod (inc (get stats :ssn 0))
                                                                 10)]
                                                    (-> stats
                                                        (assoc :ssn ssn)
                                                        (update-in [:ss ssn]
                                                                   (fn [pm]
                                                                     (render-frame pm data width height pitch)))))))
                                         (repaint!))]
    (dotimes [i num-frames]
     (retro/retro_run)))
  (let [new-state (get-state)
        new-inputs (conj inputs
                         {:controls controls
                          :num-frames num-frames})]
    (swap! state-cache assoc new-inputs new-state)
    new-inputs))

(def RETRO_MEMORY_SYSTEM_RAM  2)
(def ram-size 0x800)

(defn ^:private get-memory
  "Return the current console RAM.

  Note: this is different than the game state."
  []
  (let [mem (retro/retro_get_memory_data RETRO_MEMORY_SYSTEM_RAM)
        buf (byte-array ram-size)]
    (.read mem 0 buf 0 ram-size)
    buf))

;; target screen tile is 12
(defn ->coord
  "Combine `screen-tile`, `xpos`, and `subpixel` into a single, ordered number."
  [screen-tile xpos subpixel]
  (bit-or (bit-shift-left (bit-and 0xFF
                                   screen-tile)
                          16)
          (bit-shift-left (bit-and 0xFF
                                   xpos)
                          8)
          (bit-and subpixel
                   0xFF)))

(defn <-coord
  "Extract the `screen-tile`, `xpos`, and `subpixel` from a combined coordinate."
  [coord]
  [(bit-and 0xFF
            (bit-shift-right coord
                             16))
   (bit-and 0xFF
            (bit-shift-right coord
                             8))
   (bit-and 0xFF coord)])


;; (def target (->coord 12 0 0))

(defn dist
  "Checks the current RAM and estimates the current distance from the goal."
  [inputs]
  (let [save-state (get @state-cache inputs)]
    (retro/retro_unserialize save-state (alength save-state)))
  (let [mem (retro/retro_get_memory_data RETRO_MEMORY_SYSTEM_RAM)
        screen-tile (.getByte mem 0x006D)
        xpos (.getByte mem 0x0086)
        subpixel (.getByte mem 0x0400)

        ;; absolute
        speed (.getByte mem 0x0700)

        ;; dist (Math/abs (- target (->coord screen-tile xpos subpixel)))

        vertical-position (.getByte mem  0x00B5)
        below-viewport? (> vertical-position 1)

        on-flag-pole? (= 0x03 (.getByte mem 0x001D))
        dead? (= 3 (.getByte mem 0x0770))
        ypos (byte-format (.getByte mem 0x03B8))
        ]
    [(not on-flag-pole?)
     dead?
     below-viewport?
     (- (->coord screen-tile xpos subpixel))
     ypos
     (- speed)
     
     (count inputs)]))

(defn done?
  "Return true if we've reached our goal given the estimated distance.

  This is supposed to check for the end of the level, but it only checks for riding on the flag pole and doesn't know about dungeons."
  [[not-done? & _]]
  (not not-done?))

(def buttons
  [:RETRO_DEVICE_ID_JOYPAD_A
   :RETRO_DEVICE_ID_JOYPAD_B
   ;; :RETRO_DEVICE_ID_JOYPAD_SELECT
   ;; :RETRO_DEVICE_ID_JOYPAD_START
   ])
(def directions
  [;;:RETRO_DEVICE_ID_JOYPAD_UP
   ;; :RETRO_DEVICE_ID_JOYPAD_DOWN
   :RETRO_DEVICE_ID_JOYPAD_LEFT
   :RETRO_DEVICE_ID_JOYPAD_RIGHT
   nil])

(defn successors
  "Generates successor states given past inputs."
  [inputs]
  (into []
        (comp
         (map (fn [[buttons dir]]
                (set
                 (if dir
                   (conj buttons dir)
                   buttons))))
         (map (fn [controls]
                (next-state inputs controls
                            (inc (rand-int 60))))))
        (apply
         concat
         (repeat
          1
          (combo/cartesian-product
           (combo/subsets buttons)
           directions)))))


(defn anneal [q prob]
  (or (first (eduction
              (comp (random-sample prob)
                    (take 1))
              q))
      (first q)))

(defn anneal2 [q]
  (loop [r (seq q)
         n (count r)]
    (if (< (rand) (/ 1 (max 1 n)))
      (first r)
      (recur (next r)
             (dec n))))
  )

(defn with-lookback [q dist]
  (let [lookback (* (rand) dist)
        farthest (-> (first (vals q))
                     (nth 3))]
    (or (->> q
             (drop-while (fn [[node dist]]
                           (let [current (nth dist 3)]
                             (< (- current
                                   farthest)
                                lookback))))
             first)
        (first q))))

(defn solve
  "Given a `start` state, a distance estimating function, a `successors` function, and a function that says when we're `done`,
  Try to find a path from the start to the solution."
  ([start dist successors done?]
   (solve (priority-map start (dist start))
          dist
          successors
          done?
          #{start}))
  ([queue distf successorsf done? visited]
   (if (and ;;(< (count visited) 56000)
        (not (Thread/interrupted)))
     (when-let [[node dist]
                (with-lookback queue (->coord 1 0 0))
                
                #_(rand-nth (->> queue
                                 (take (max 1
                                            (int (* 0.1 (count queue)))))))
                #_(anneal queue (/ 100
                                   (max 1
                                        (count queue))))
                #_(-> queue peek)]
       (let [[not-done? dead? below-viewport? xpos speed frames] dist]
        (swap! stats
               assoc
               :queue-count (count queue)
               :dist dist))
       (if (done? dist)
         node
         (let [successors (successorsf node)
               queue (-> queue
                         (into (for [successor successors
                                     :when (not (contains? visited successor))]
                                 [successor (distf successor)]))
                         (dissoc node)
                         )
               visited (into visited successors)]
           (recur queue
                  distf
                  successorsf
                  done?
                  visited
                  ))))
     {:fail true
      :queue queue
      :visited visited})))




(defn ^:private init! [game-path]
  (retro/retro_set_environment #'set-environment)
  (retro/retro_init)


  (retro/retro_set_video_refresh #'video-refresh-callback)
  (retro/retro_set_audio_sample #'audio-sample)


  (retro/retro_set_input_poll #'input-poll)
  (retro/retro_set_input_state #'input-state-callback)
  (retro/retro_set_audio_sample_batch #'audio-sample-batch-callback)

  (retro/retro_load_game (game-info game-path)))

(defn ^:private deinit! []
  (retro/retro_deinit))

(comment

  ;; need to initialize glfw before running solver.
  (skia/run nil)

  (do
    (init! "Super Mario Bros. (World).nes")
    (skia/run
      (fn []
        (let [stat @stats]
          (ui/vertical-layout
           (ui/label (pr-str (dissoc stat :ss)))
           (get-in stat [:ss (get stat :ssn)]))))
      {:window-start-width 500
       :window-start-height 300})

    (load-save "ui.save")
    (swap! state-cache assoc [] (get-state))

    (def results
      (solve []
             dist
             successors
             done?)))
  ,)




(defui image-viewer [{:keys [imgs n]}]
  (let [n (or n 0)
        img (nth imgs n)]
    (ui/vertical-layout
     (basic/number-slider {:num n
                           :min 0
                           :max (dec (count imgs))
                           :integer? true})
     img)))

(defn path->imgs [path]
  (into []
        (comp (map (fn [n]
                     (load-state (get @state-cache (take n path)))
                     (screenshot))))
        (range (count path) )))
(def path->imgs-memo (memoize path->imgs))

(defui results-viewer [{:keys [results n]}]
  (let [n (or n 0)
        result (-> results
                        :queue
                        keys
                        (nth n))]

    (ui/vertical-layout
     (basic/number-slider {:num n
                           :min 0
                           :max 10
                           :integer? true})
     (let [img-n (get extra [n :img-n])]
       (image-viewer {:n img-n
                      :imgs (path->imgs-memo result)})))))



(comment

  (skia/run
    (component/make-app #'image-viewer
                        {:imgs (path->imgs results)
                         :n 0}))
  

  (skia/run
    (component/make-app #'results-viewer
                        {:results results
                         :n 0}))
  
  ,)


;; make movie
(comment
  

  (do
    (init! "Super Mario Bros. (World).nes")
    (load-save "ui.save")
    (def frames
      (into []
            (mapcat (fn [{:keys [controls num-frames]}]
                      (let [frames (volatile! [])]
                        (with-redefs [input-state-callback
                                      (fn [port device index id]
                                        (if (= port 0)
                                          (if (controls (c/device-name id))
                                            1
                                            0)
                                          0))
                                      video-refresh-callback (fn [data width height pitch]
                                                               (vswap! frames conj (render-frame nil data width height pitch)))]
                          (dotimes [i num-frames]
                            (retro/retro_run)))
                        @frames)))

            
            (if (:fail results)
              (-> results :queue keys first)
              results))))

  (require '[com.phronemophobic.clj-media.skia :as media])
  (media/write-video "mairio-level-3-4.mp4"
                     frames
                     (ui/width (first frames))
                     (ui/height (first frames))
                     
                     )

  ,)

