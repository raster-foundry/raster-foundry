package com.azavea.rf.migration.manager

import com.liyaos.forklift.slick.SlickRescueCommands
import com.liyaos.forklift.slick.SlickRescueCommandLineTool

object Tool extends App
    with SlickRescueCommandLineTool
    with SlickRescueCommands
    with RFCodegen {
  execCommands(args.toList)
}
