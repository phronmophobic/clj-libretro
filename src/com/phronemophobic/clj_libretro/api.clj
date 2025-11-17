(ns com.phronemophobic.clj-libretro.api
  (:require [clojure.edn :as edn]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [com.phronemophobic.clong.gen.jna :as gen])
  (:import com.sun.jna.Memory
           com.sun.jna.ptr.ByteByReference
           com.sun.jna.Structure))



(defn ^:private write-edn [w obj]
  (binding [*print-length* nil
            *print-level* nil
            *print-dup* false
            *print-meta* false
            *print-readably* true

            ;; namespaced maps not part of edn spec
            *print-namespace-maps* false

            *out* w]
    (pr obj)))

(defn ^:private dump-api []
  (let [outf (io/file
              "resources"
              "com"
              "phronemophobic"
              "clj-libretro"
              "api.edn")]
    (.mkdirs (.getParentFile outf))
    (with-open [w (io/writer outf)]
      (write-edn w
                 ((requiring-resolve 'com.phronemophobic.clong.clang/easy-api)
                  "/Users/adrian/workspace/libretro-super/libretro-fceumm/src/drivers/libretro/libretro-common/include/libretro.h")
                 ))))

(def api
  (-> (with-open [rdr (io/reader
                       (io/resource
                        "com/phronemophobic/clj-libretro/api.edn"))
                  rdr (java.io.PushbackReader. rdr)]
        (edn/read rdr))
      #_((requiring-resolve 'com.phronemophobic.clong.clang/easy-api) "/Users/adrian/workspace/libretro-super/libretro-fceumm/src/drivers/libretro/libretro-common/include/libretro.h")
      (gen/replace-forward-references)))


(defprotocol IRetro
  (retro_set_environment [this env-callback]
    "Sets callbacks. retro_set_environment() is guaranteed to be called
before retro_init().

The rest of the set_* functions are guaranteed to have been called
before the first call to retro_run() is made.")

  (retro_set_video_refresh [this video-refresh-callback])

  (retro_set_audio_sample [this audio-sample-callback])

  (retro_set_audio_sample_batch [this audo-sample-batch-callback])

  (retro_set_input_poll [this poll-input-callback])

  (retro_set_input_state [this input-state-callback])


  (retro_init [this]
    "Library global initialization")

  (retro_deinit [this]
    "Library global deinitialization")
  (retro_api_version [this]
    "Must return RETRO_API_VERSION. Used to validate ABI when the API is revised.")

  (retro_get_system_info [this info]
    "Gets statically known system info. Pointers provided in *info
must be statically allocated.
Can be called at any time, even before retro_init().")

  (retro_get_system_av_info [this info]
    "Gets information about system audio/video timings and geometry.
Can be called only after retro_load_game() has successfully completed.
NOTE: The implementation of this function might not initialize every
variable if needed.
E.g. geom.aspect_ratio might not be initialized if core doesn't
desire a particular aspect ratio.")

  (retro_set_controller_port_device [this port device]
    "Sets device to be used for player 'port'.
By default, RETRO_DEVICE_JOYPAD is assumed to be plugged into all
available ports.
Setting a particular device type is not a guarantee that libretro cores
will only poll input based on that particular device type. It is only a
hint to the libretro core when a core cannot automatically detect the
appropriate input device type on its own. It is also relevant when a
core can change its behavior depending on device type.
 *
As part of the core's implementation of retro_set_controller_port_device,
the core should call RETRO_ENVIRONMENT_SET_INPUT_DESCRIPTORS to notify the
frontend if the descriptions for any controls have changed as a
result of changing the device type.
")

  (retro_reset [this]
    "Resets the current game.")

  (retro_run [this]
   "Runs the game for one video frame.
During retro_run(), input_poll callback must be called at least once.
 *
If a frame is not rendered for reasons where a game \"dropped\" a frame,
this still counts as a frame, and retro_run() should explicitly dupe
a frame if GET_CAN_DUPE returns true.
In this case, the video callback can take a NULL argument for data.
")

  (retro_serialize_size [this]
   "Returns the amount of data the implementation requires to serialize
internal state (save states).
Between calls to retro_load_game() and retro_unload_game(), the
returned size is never allowed to be larger than a previous returned
value, to ensure that the frontend can allocate a save state buffer once.
")

  (retro_serialize [this data size]
   "Serializes internal state. If failed, or size is lower than
retro_serialize_size(), it should return false, true otherwise.")

  (retro_unserialize [this data size])

  (retro_cheat_reset [this])

  (retro_cheat_set [this index enabled code])

  (retro_load_game [this game]
    "Loads a game.
Return true to indicate successful loading and false to indicate load failure.
")

  (retro_load_game_special [this game_type info num_info]
    "Loads a \"special\" kind of game. Should not be used,
except in extreme cases.")

  (retro_unload_game [this]
    "Unloads the currently loaded game. Called before retro_deinit(void).")

  (retro_get_region [this]
    "Gets region of game.")

  (retro_get_memory_data [this id]
    "Gets region of memory.")

  (retro_get_memory_size [this id]))

(def struct-prefix "com.phronemophobic.clj_libretro.api.structs")

(gen/def-enums (:enums api))
(gen/def-structs (:structs api) struct-prefix) 

(gen/import-structs! api)

(let [struct-prefix (gen/ns-struct-prefix *ns*)]
  (defmacro import-structs! []
    `(gen/import-structs! api ~struct-prefix)))

(defn ^:private write-string [^Structure struct field ^String s]
  (let [bytes (.getBytes s "utf-8")
        mem (doto (Memory. (inc (alength bytes)))
              (.write 0 bytes 0 (alength bytes))
              (.setByte (alength bytes) 0))
        bbr (doto (ByteByReference.)
              (.setPointer mem))]
    (doto struct
      (.writeField field bbr))))



(defn get-av-info [iretro]
  (let [s (retro_system_av_infoByReference.)]
    (retro_get_system_av_info iretro s)
    (let [^Structure
          timing (.readField s "timing")
          ^Structure
          geometry (.readField s "geometry")]
      {:timing {:fps (.readField timing "fps")
                :sample-rate (.readField timing "sample_rate")}
       :geometry {:base-width (.readField geometry "base_width")
                  :base-height (.readField geometry "base_height")
                  :max-width (.readField geometry "max_width")
                  :max-height (.readField geometry "max_height")
                  :aspect-ratio (.readField geometry "aspect_ratio")}})))

(defn game-info [path]
  (let [game (retro_game_infoByReference.)]
    (doto game
      (write-string "path" path)
      (.writeField "data" nil)
      (.writeField "size" 0)
      (.writeField "meta" nil))))


(defn ^:private load-core-lib* [lib]
  (let [functions-by-name (into {}
                                (map (juxt :symbol identity))
                                (:functions api))
        name->fn (fn [fn-name]
                   (let [f (get functions-by-name fn-name)
                         fn-ast (gen/fn-ast struct-prefix f)
                         fn-code (:->fn fn-ast)
                         ->fn (eval fn-code)]
                     (->fn lib)))

        -retro_api_version                (name->fn "retro_api_version")
        -retro_cheat_reset                (name->fn "retro_cheat_reset")
        -retro_cheat_set                  (name->fn "retro_cheat_set")
        -retro_deinit                     (name->fn "retro_deinit")
        -retro_get_memory_data            (name->fn "retro_get_memory_data")
        -retro_get_memory_size            (name->fn "retro_get_memory_size")
        -retro_get_region                 (name->fn "retro_get_region")
        -retro_get_system_av_info         (name->fn "retro_get_system_av_info")
        -retro_get_system_info            (name->fn "retro_get_system_info")
        -retro_init                       (name->fn "retro_init")
        -retro_load_game                  (name->fn "retro_load_game")
        -retro_load_game_special          (name->fn "retro_load_game_special")
        -retro_reset                      (name->fn "retro_reset")
        -retro_run                        (name->fn "retro_run")
        -retro_serialize                  (name->fn "retro_serialize")
        -retro_serialize_size             (name->fn "retro_serialize_size")
        -retro_set_audio_sample           (name->fn "retro_set_audio_sample")
        -retro_set_audio_sample_batch     (name->fn "retro_set_audio_sample_batch")
        -retro_set_controller_port_device (name->fn "retro_set_controller_port_device")
        -retro_set_environment            (name->fn "retro_set_environment")
        -retro_set_input_poll             (name->fn "retro_set_input_poll")
        -retro_set_input_state            (name->fn "retro_set_input_state")
        -retro_set_video_refresh          (name->fn "retro_set_video_refresh")
        -retro_unload_game                (name->fn "retro_unload_game")
        -retro_unserialize                (name->fn "retro_unserialize")]
    (reify IRetro
      (retro_set_environment [_ env-callback]
        (-retro_set_environment env-callback))

      (retro_set_video_refresh [_ video-refresh-callback]
        (-retro_set_video_refresh video-refresh-callback))

      (retro_set_audio_sample [_ audio-sample-callback]
        (-retro_set_audio_sample audio-sample-callback))

      (retro_set_audio_sample_batch [_ audo-sample-batch-callback]
        (-retro_set_audio_sample_batch audo-sample-batch-callback))

      (retro_set_input_poll [_ poll-input-callback]
        (-retro_set_input_poll poll-input-callback))

      (retro_set_input_state [_ input-state-callback]
        (-retro_set_input_state input-state-callback))

      (retro_init [_]
        (-retro_init))

      (retro_deinit [_]
        (-retro_deinit))
      (retro_api_version [_]
        (-retro_api_version))

      (retro_get_system_info [_ info]
        (-retro_get_system_info info))

      (retro_get_system_av_info [_ info]
        (-retro_get_system_av_info info))

      (retro_set_controller_port_device [_ port device]
        (-retro_set_controller_port_device port device))

      (retro_reset [_]
        (-retro_reset))

      (retro_run [_]
        (-retro_run))

      (retro_serialize_size [_]
        (-retro_serialize_size))

      (retro_serialize [_ data size]
        (-retro_serialize data size))

      (retro_unserialize [_ data size]
        (-retro_unserialize data size))

      (retro_cheat_reset [_]
        (-retro_cheat_reset))

      (retro_cheat_set [_ index enabled code]
        (-retro_cheat_set index enabled code))

      (retro_load_game [_ game]
        (-retro_load_game game))

      (retro_load_game_special [_ game_type info num_info]
        (-retro_load_game_special game_type info num_info))

      (retro_unload_game [_]
        (-retro_unload_game))

      (retro_get_region [_]
        (-retro_get_region))

      (retro_get_memory_data [_ id]
        (-retro_get_memory_data id))

      (retro_get_memory_size [_ id]
        (-retro_get_memory_size id)))))

(defn ^:private load-core [core-name]
  (load-core-lib* (com.sun.jna.NativeLibrary/getInstance (str "retro_" core-name))))

(let [load-core-memo (memoize load-core)]
  (defn load-core
    "Loads a core that satisfies IRetro.

  examples:
  (load-core \"fceumm\")
  (load-core \"snes9x\")"
    [core-name]
    (load-core-memo core-name)))

(let [load-core-lib-memo (memoize load-core-lib*)]
  (defn load-core-lib
    "Loads a lib that satisfies IRetro.
    
    You should probably be using load-core."
    [lib]
    (load-core-lib-memo lib)))
