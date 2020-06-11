package org.simple.clinic.introvideoscreen

sealed class IntroVideoEvent

object VideoClicked : IntroVideoEvent()

object SkipClicked : IntroVideoEvent()
