define [
  'underscore',
  'backbone'
  'i18n'
], (_, Backbone, i18n) ->
  t = i18n.namespaced('views.DocumentSet._massUploadForm')

  class UploadCollectionView extends Backbone.View
    events:
      'scroll': '_onScroll'

    template: _.template("""
      <ul>
        <li class="empty-upload">
          <i class='icon-cloud-upload'></i>
          <div><%- t('drop_target') %></div>
          <div class='secondary-prompt'><%- t('minimum_files') %></div>
        </li>
      </ul>
    """)

    initialize: ->
      throw 'Must pass uploadViewClass, a Backbone.View class' if !@options.uploadViewClass?
      @_views = [] # keep track of views, so we can remove() them
      @_pendingViews = [] # keep track of uploads: views that haven't been instantiated yet

      @_scrollTop = 0
      @_cidToIndex = {}

      @listenTo(@collection, 'add', @_onAdd)
      @listenTo(@collection, 'change', @_onChange)

    remove: ->
      @_removeAll()
      super()

    _removeAll: ->
      @_views.forEach((v) -> v.remove()) # Ends up calling stopListening()

    _onAdd: (upload) ->
      if @_emptyUploadIsPresent
        @_emptyUploadIsPresent = false
        @_$emptyUpload.remove()

      @_cidToIndex[upload.cid] = @_views.length + @_pendingViews.length
      @_pendingViews.push(upload)
      @_materializeVisiblePendingViews()

    _onChange: (upload) ->
      idx = @_cidToIndex[upload.cid]

      return if !idx? || !@_liHeight?

      # Scroll so there's always one item below the changed one
      bottom = @_liHeight * (idx + 2)
      top = Math.max(0, bottom - @_elHeight)

      @$el.scrollTop(top)
      @$el.scroll()

    _createRenderAndAddViewFor: (upload) ->
      view = new @options.uploadViewClass(model: upload)
      view.render()
      @_views.push(view)
      @_$ul.append(view.el)

    # Shifts the required number of elements from the beginning of
    # @_pendingViews to the end of @_views.
    #
    # After this method completes, all lis that are in this view's visible
    # scroll area will be in @_views. (Uploads below the scroll limit will stay
    # in @_pendingViews.)
    #
    # This mechanism means there are only a few dozen renders at once, even if
    # we upload 10,000 files.
    _materializeVisiblePendingViews: ->
      # Materialize the first view: then we can find out @_ulHeight, @_liHeight
      if !@_liHeight?
        @_createRenderAndAddViewFor(@_pendingViews.shift())
        @_liHeight = @_$ul.children(':eq(0)').outerHeight()
        @_elHeight = @$el.height()

      nVisible = Math.ceil((@_scrollTop + @_elHeight) / @_liHeight)

      nNeeded = nVisible - @_views.length

      if nNeeded > 0
        for upload in  @_pendingViews.splice(0, nNeeded)
          @_createRenderAndAddViewFor(upload)

      @_$ul.height(@_liHeight * (@_views.length + @_pendingViews.length))

    _onScroll: ->
      @_scrollTop = @$el.scrollTop()
      @_materializeVisiblePendingViews()

    _onReset: ->
      @_removeAll()
      @collection.each(@_onAdd, @)

    render: ->
      html = @template(t: t)
      @$el.html(html)
      @_$ul = @$('ul')
      @_$emptyUpload = @$('li.empty-upload')
      @_emptyUploadIsPresent = true
      @_onReset()
