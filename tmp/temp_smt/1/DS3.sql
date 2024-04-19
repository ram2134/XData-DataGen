delete from prereq
delete from takes
delete from teaches
delete from section
delete from course
delete from advisor
delete from instructor
delete from student
delete from department
delete from classroom
insert into classroom values ('Packard','3128','416')
insert into classroom values ('Taylor','120','10')
insert into department values ('Music','Taylor','65974.0')
insert into department values ('Finance','Packard','54263.0')
insert into department values ('Comp. Sci.','Taylor','50000.0')
insert into department values ('Finance','Packard','54263.0')
insert into department values ('Physics','Painter','53143.0')
insert into department values ('Music','Taylor','65974.0')
insert into department values ('History','Taylor','50000.0')
insert into instructor values ('15151','Einstein',null,'88702.5')
insert into instructor values ('10101','Einstein',null,'40000.0')
insert into instructor values ('12121','Einstein','Finance','53403.5')
insert into course values ('BIO-301','Music Video Production','Music','4')
insert into course values ('CS-347','Robotics',null,'3')
insert into course values ('BIO-301','Music Video Production','Music','4')
insert into course values ('CS-347','Robotics',null,'3')
insert into section values ('BIO-301','1','Summer','2009','Packard','3128','C')
insert into section values ('CS-347','2','Summer','2009','Taylor','120','B')
insert into teaches values ('12121','BIO-301','1','Summer','2009')
insert into teaches values ('15151','CS-347','2','Summer','2009')
