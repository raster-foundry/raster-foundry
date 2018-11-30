import com.liyaos.forklift.slick._

object RFMigrations
    extends App
    with SlickMigrationCommandLineTool
    with SlickMigrationCommands
    with SlickMigrationManager
    with SlickCodegen {
  MigrationSummary
  execCommands(args.toList)
}
