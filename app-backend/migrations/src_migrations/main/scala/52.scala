import slick.jdbc.PostgresProfile.api._
import com.liyaos.forklift.slick.SqlMigration

object M52 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(52)(
    List(
      sqlu"""
    INSERT INTO feature_flags (id, key, active, name, description) VALUES
      (
        '68144a5a-bf71-4925-b2f6-94893333c88d',
        'profile-org-edit',
        FALSE,
        'Profile Organization Edit',
        'Join / Create / Edit / Leave / Delete organizations in user profile settings'
      ),
      (
        '76ffc49d-f92f-4f4f-99ad-40e8b1ff529f',
        'make-source-histogram',
        FALSE,
        'Make source histogram',
        'Show or hide the "make source histogram" link in color correction'
      ),
      (
        '515c2eb5-7255-4c31-aef8-8423ba15d6c7',
        'market-search',
        FALSE,
        'Market Search',
        'Allow the market search feature to be visible'
      ),
      (
        '99e38005-0ae5-41cd-bbea-330bda92499a',
        'display-histogram',
        FALSE,
        'Display Histogram',
        'Display and fetch histograms for color correction'
      ),
      (
        'a75d427f-576d-4850-96c3-895bbf384098',
        'tools-ui',
        TRUE,
        'Enable Tools UI',
        'Enable UI elements providing access to Tools functionality'
      );
    """
    ))
}
