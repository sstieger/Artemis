import { Injectable } from '@angular/core';
import { SERVER_API_URL } from 'app/app.constants';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { LearningGoal } from 'app/entities/learningGoal.model';
import { LectureUnitService } from 'app/lecture/lecture-unit/lecture-unit-management/lectureUnit.service';
import { map } from 'rxjs/operators';

type EntityResponseType = HttpResponse<LearningGoal>;
type EntityArrayResponseType = HttpResponse<LearningGoal[]>;

@Injectable({
    providedIn: 'root',
})
export class LearningGoalService {
    private resourceURL = SERVER_API_URL + 'api';

    constructor(private httpClient: HttpClient, private lectureUnitService: LectureUnitService) {}

    getAllForCourse(courseId: number): Observable<EntityArrayResponseType> {
        return this.httpClient.get<LearningGoal[]>(`${this.resourceURL}/courses/${courseId}/goals`, { observe: 'response' });
    }

    findById(learningGoalId: number, courseId: number) {
        return this.httpClient
            .get<LearningGoal>(`${this.resourceURL}/courses/${courseId}/goals/${learningGoalId}`, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.convertDateFromServerResponse(res)));
    }

    create(learningGoal: LearningGoal, courseId: number): Observable<EntityResponseType> {
        const copy = this.convertDateFromClient(learningGoal);
        return this.httpClient.post<LearningGoal>(`${this.resourceURL}/courses/${courseId}/goals`, copy, { observe: 'response' });
    }

    update(learningGoal: LearningGoal, courseId: number): Observable<EntityResponseType> {
        const copy = this.convertDateFromClient(learningGoal);
        return this.httpClient.put(`${this.resourceURL}/courses/${courseId}/goals`, copy, { observe: 'response' });
    }

    delete(learningGoalId: number, courseId: number) {
        return this.httpClient.delete(`${this.resourceURL}/courses/${courseId}/goals/${learningGoalId}`, { observe: 'response' });
    }

    convertDateFromServerResponse(res: EntityResponseType): EntityResponseType {
        if (res.body?.lectureUnits) {
            res.body.lectureUnits = this.lectureUnitService.convertDateArrayFromServerEntity(res.body.lectureUnits);
        }
        return res;
    }

    convertDateFromClient(learningGoal: LearningGoal): LearningGoal {
        const copy = Object.assign({}, learningGoal);
        if (copy.lectureUnits) {
            copy.lectureUnits = this.lectureUnitService.convertDateArrayFromClient(copy.lectureUnits);
        }
        return copy;
    }
}
