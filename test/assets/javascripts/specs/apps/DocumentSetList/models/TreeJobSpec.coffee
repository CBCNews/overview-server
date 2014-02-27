define [ 'apps/DocumentSetList/models/TreeJob' ], (TreeJob) ->
  describe 'apps/DocumentSetList/models/TreeJob', ->
    it 'should be okay with just an ID', ->
      subject = new TreeJob(id: 34)
      expect(subject.attributes).toEqual(id: 34)

