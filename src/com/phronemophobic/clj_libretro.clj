(ns com.phronemophobic.clj-libretro
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            clojure.math
            [com.phronemophobic.clj-libretro.constants :as c]
            ;; [membrane.java2d :as java2d]
            [membrane.skia :as skia]
            [membrane.ui :as ui]
            [clojure.pprint :refer [pprint]]
            [clojure.edn :as edn]
            [com.phronemophobic.clong.clang :as clong]
            [com.phronemophobic.clong.gen.jna :as gen])
  
  (:import
   java.util.concurrent.LinkedBlockingQueue
   ;; java.awt.image.BufferedImage
   ;; javax.imageio.ImageIO
   java.io.ByteArrayOutputStream
   java.io.PushbackReader
   com.sun.jna.Memory
   com.sun.jna.Pointer
   com.sun.jna.ptr.PointerByReference
   com.sun.jna.ptr.ByteByReference
   com.sun.jna.ptr.LongByReference
   com.sun.jna.ptr.IntByReference
   com.sun.jna.Structure
   java.io.ByteArrayInputStream
   (javax.sound.sampled AudioFormat
                        AudioFormat$Encoding
                        AudioInputStream
                        AudioSystem
                        DataLine
                        DataLine$Info
                        Line
                        LineUnavailableException
                        SourceDataLine
                        UnsupportedAudioFileException
                        )))




;; (retro_set_environment set-environment)
;; (retro_init)
;; (retro_set_video_refresh video-refresh)
;; (retro_set_audio_sample audio-sample)
;; (retro_set_audio_sample_batch  audio-sample-batch)
;; (retro_set_input_poll input-poll)
;; (retro_set_input_state input-state))
