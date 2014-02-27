define [
  'jquery'
  '../models/TreeJob'
  '../collections/TreeJobs'
  '../views/TreeJobView'
], ($, TreeJob, TreeJobs, TreeJobView) ->
  class TreeJobController
    constructor: (options) ->
      @el = options.el
      @url = options.url
      @delay = options.delay
      throw 'Must pass el, an HTML element' if !@el?
      throw 'Must pass url, a URL for refreshing trees' if !@url?
      throw 'Must pass delay, an amount of time to wait between polls (0 for synchronous -- useful only for testing!)' if !@delay?

      @$el = $(@el)

      @_parseEl()

      @collection.url = @url

      @_delayedPoll()

    _parseEl: ->
      models = []
      views = []

      for div in @$el.find('div[data-job-id]:not(.error):not(.cancelled)')
        id = div.getAttribute('data-job-id')
        model = new TreeJob(id: id)
        view = new TreeJobView(model: model, el: div)

        models.push(model)
        views.push(view)

      @collection = new TreeJobs(models)
      @views = views
      undefined

    _delayedPoll: ->
      if @delay == 0
        @_poll()
      else
        window.setTimeout((=> @_poll()), @delay)

    _poll: ->
      @collection.sync()
