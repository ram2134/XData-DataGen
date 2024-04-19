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
insert into classroom values ('Packard','3128','462')
insert into classroom values ('Taylor','120','10')
insert into department values ('History','Packard','67059.0')
insert into department values ('Finance','Painter','50000.0')
insert into department values ('Music','Taylor','62572.0')
insert into department values ('Finance','Painter','50000.0')
insert into department values ('Finance','Painter','50000.0')
insert into department values ('Physics','Taylor','62571.0')
insert into department values ('Finance','Painter','50000.0')
insert into instructor values ('15151','Einstein','Finance','46798.0')
insert into instructor values ('10101','Katz',null,'40000.0')
insert into instructor values ('12121','Einstein',null,'82454.5')
insert into course values ('BIO-301','Music Video Production',null,'3')
insert into course values ('CS-347','Robotics','Finance','3')
insert into course values ('BIO-301','Music Video Production',null,'3')
insert into course values ('CS-347','Robotics','Finance','3')
insert into section values ('BIO-301','2','Spring','2010','Packard','3128','C')
insert into section values ('CS-347','2','Summer','2009','Taylor','120','B')
insert into teaches values ('12121','BIO-301','2','Spring','2010')
insert into teaches values ('15151','CS-347','2','Summer','2009')
