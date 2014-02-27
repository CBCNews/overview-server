define [
  'backbone'
  '../models/TreeJob'
], (Backbone, TreeJob) ->
  class TreeJobs extends Backbone.Collection
    model: TreeJob
