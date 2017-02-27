package com.azavea.rf.datamodel

object RelatedManager {

  trait HasRelations1[A] {
    type Arg
    type Out
    def relates(self: A, other: Arg): Out
  }

  object HasRelations1 {
    type Aux[In, Arg0, Out0] = HasRelations1[In] { type Arg = Arg0; type Out = Out0 }

    def apply[A, B, C](f: (A, B) => C): HasRelations1.Aux[A, B, C] =
      new HasRelations1[A] {
        type Arg = B
        type Out = C
        def relates(self: A, related: Arg): Out = f(self, related)
      }

    implicit val ImageRelatesBand = HasRelations1 {
      (self: Image, related: Seq[Band]) => Image.WithRelated(
        self.id,
        self.createdAt,
        self.modifiedAt,
        self.organizationId,
        self.createdBy,
        self.modifiedBy,
        self.rawDataBytes,
        self.visibility,
        self.filename,
        self.sourceUri,
        self.scene,
        self.imageMetadata,
        self.resolutionMeters,
        self.metadataFiles,
        related
      )
    }

    implicit class withRelateMethod1[A](val self: A) {
      def relates[B, C](related: B)(implicit ev: HasRelations1.Aux[A, B, C]): C = ev.relates(self, related)
    }
  }

  trait HasRelations2[A] {
    type Arg1
    type Arg2
    type Out
    def relates(self: A, other1: Arg1, other2: Arg2): Out
  }

  object HasRelations2 {
    type Aux[In, Arg10, Arg20, Out0] = HasRelations2[In] {
      type Arg1 = Arg10
      type Arg2 = Arg20
      type Out = Out0
    }

    def apply[A, B, C, D](f: (A, B, C) => D): HasRelations2.Aux[A, B, C, D] =
      new HasRelations2[A] {
        type Arg1 = B
        type Arg2 = C
        type Out = D
        def relates(self: A, related1: Arg1, related2: Arg2): Out =
          f(self, related1, related2)
      }

    implicit val sceneRelatesImagesThumbnails = HasRelations2 {
      (self: Scene, related1: Seq[Thumbnail], related2: Seq[(Image, Seq[Band])]) => {
        val imagesWithRelated = related2 map {
          case (im, bs) => im.withRelatedFromComponents(bs)
        }
        Scene.WithRelated(
          self.id,
          self.createdAt,
          self.createdBy,
          self.modifiedAt,
          self.modifiedBy,
          self.organizationId,
          self.ingestSizeBytes,
          self.visibility,
          self.tags,
          self.datasource,
          self.sceneMetadata,
          self.name,
          self.tileFootprint,
          self.dataFootprint,
          self.metadataFiles,
          imagesWithRelated,
          related1,
          self.ingestLocation,
          self.filterFields,
          self.statusFields
        )
      }
    }

    implicit class withRelatedMethod2[A](val self: A) {
      def relates[B, C, D](related1: B, related2: C)
                 (implicit ev: HasRelations2.Aux[A, B, C, D]): D =
        ev.relates(self, related1, related2)
    }
  }

  trait CanBuildFromRecords2[A] {
    type Base
    type Other
    def fromRecords(records: Seq[(Base, Other)]): Iterable[A]
  }

  object CanBuildFromRecords2 {
    type Aux[A0, Base0, Other0] = CanBuildFromRecords2[A0] { type Base = Base0; type Other = Other0 }

    def apply[A, B, C](f: Seq[(B, C)] => Iterable[A]): CanBuildFromRecords2.Aux[A, B, C] =
      new CanBuildFromRecords2[A] {
        type Base = B
        type Other = C
        def fromRecords(records: Seq[(B, C)]): Iterable[A] = f(records)
      }

    implicit val ImageWithRelatedFromRecords = CanBuildFromRecords2 {
      (records: Seq[(Image, Band)]) => {
        Image.WithRelated.fromRecords(records): Iterable[Image.WithRelated]
      }
    }

    implicit class canBuildFromRecords2[A](val self: A) {
      def fromRecords[B, C](records: Seq[(B, C)])
                     (implicit ev: CanBuildFromRecords2.Aux[A, B, C]): Iterable[A] = ev.fromRecords(records)
    }
  }

  trait CanBuildFromRecords4[A] {
    type Base
    type Other1
    type Other2
    type Other3
    def fromRecords(records: Seq[(Base, Other1, Other2, Other3)]): Iterable[A]
  }

  object CanBuildFromRecords4 {
    type Aux[A0, Base0, Other10, Other20, Other30] = CanBuildFromRecords4[A0] {
      type Base = Base0
      type Other1 = Other10
      type Other2 = Other20
      type Other3 = Other30
    }

    def apply[A, B, C, D, E](f: Seq[(B, C, D, E)] => Iterable[A]): CanBuildFromRecords4.Aux[A, B, C, D, E] =
      new CanBuildFromRecords4[A] {
        type Base = B
        type Other1 = C
        type Other2 = D
        type Other3 = E
        def fromRecords(records: Seq[(B, C, D, E)]): Iterable[A] = f(records)
      }

    implicit val SceneWithRelatedFromRecords = CanBuildFromRecords4 {
      (records: Seq[(Scene, Option[Image], Option[Band], Option[Thumbnail])]) =>
        Scene.WithRelated.fromRecords(records)
    }

    implicit class canBuildFromRecords4[A](val self: A) {
      def fromRecords[B, C, D, E](records: Seq[(B, C, D, E)])
                     (implicit ev: CanBuildFromRecords4.Aux[A, B, C, D, E]): Iterable[A] =
        ev.fromRecords(records)
    }
  }
}

