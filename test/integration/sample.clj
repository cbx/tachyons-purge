(ns sample
  (:require [hiccup.core :refer [html]]))

(defn page []
  [:div.pa3.bg-blue.white
   [:h1.f2.mb3 "Hello"]
   [:nav.flex.items-center.justify-between
    [:a {:class "link dim"} "Home"]]])
