define [
  'jquery'
  'backbone'
  'apps/DocumentSetList/controllers/TreeJobController'
], ($, backbone, TreeJobController) ->
  describe 'apps/DocumentSetList/controllers/TreeJobController', ->
    $html = undefined
    subject = undefined
    response = undefined

    beforeEach ->
      $html = $("""
        <div>
          <div>
            <div class="not-started" data-job-id="34">
              <span class="status-description"></span>
            </div>
          </div>
          <div>
            <div class="in-progress" data-job-id="45">
              <span class="status-description"></span>
            </div>
          </div>
          <div>
            <div class="error" data-job-id="46">
              <span class="status-description"></span>
            </div>
          </div>
          <div>
            <div class="cancelled" data-job-id="47">
              <span class="status-description"></span>
            </div>
          </div>
          <div>
            <div class="preparing" data-job-id="48">
              <span class="status-description"></span>
            </div>
          </div>
        </div>
      """)

      spyOn($, 'ajax').andReturn(response = $.Deferred())

      subject = new TreeJobController
        el: $html[0]
        url: '/trees.json'
        delay: 0

    it 'should create the models', -> expect(subject.collection.length).toEqual(3)
    it 'should create the views', -> expect(subject.views.length).toEqual(3)
    it 'should not create a cancelled model', -> expect(subject.collection.get(47)).toBeUndefined()
    it 'should not create an error model', -> expect(subject.collection.get(46)).toBeUndefined()
    it 'should wire the collections to the views', ->
      subject.collection.get(34).set(state: 'test')
      expect($html.find('[data-job-id="34"]')).toHaveClass('test')
    it 'should poll the server', -> expect($.ajax).toHaveBeenCalled()

    describe 'when the server responds', ->
      beforeEach ->
        response.resolve()
