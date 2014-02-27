define [
  'apps/DocumentSetList/collections/TreeJobs'
  'apps/DocumentSetList/models/TreeJob'
], (TreeJobs, TreeJob) ->
  describe 'apps/DocumentSetList/collections/TreeJobs', ->
    it 'should convert input to TreeJobs', ->
      subject = new TreeJobs([{ id: 10 }, { id: 20 }])
      expect(subject.at(0).id).toEqual(10)
